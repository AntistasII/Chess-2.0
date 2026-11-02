package chess.game;

import chess.config.GameProperties;
import chess.opponent.Opponent;
import chess.opponent.OpponentRegistry;

import java.io.IOException;
import java.util.Map;

/**
 * Одна текущая партия сервера или приложения. Последовательно выполняет команды,
 * отклоняя устаревшие по номеру версии, и следит за очередью хода. От HTTP не зависит.
 *
 * <p>Ход автоматического соперника выполняет {@link OpponentTurn}, файл сохранения —
 * {@link SaveFile}, представление для интерфейса — {@link GameView}.</p>
 */
public final class GameSession {
    private Game game;
    private final OpponentRegistry opponents;
    private final GameProperties settings;
    private final SaveFile saveFile;
    private boolean opponentRunning;

    public GameSession(GameProperties settings, String mode, MoveSelector localAi, MoveSelector chatGpt) {
        this(settings, mode, localAi, chatGpt, new SaveFile(SaveFile.DEFAULT));
    }

    public GameSession(GameProperties settings, String mode, MoveSelector localAi, MoveSelector chatGpt, SaveFile saveFile) {
        this(createGame(settings, GameMode.fromId(mode), null, null), settings, localAi, chatGpt, saveFile);
    }

    /** Позволяет подключить готовую позицию без замены её настроек. */
    public GameSession(Game game, GameProperties settings, MoveSelector localAi, MoveSelector chatGpt) {
        this(game, settings, localAi, chatGpt, new SaveFile(SaveFile.DEFAULT));
    }

    private GameSession(Game game, GameProperties settings, MoveSelector localAi, MoveSelector chatGpt, SaveFile saveFile) {
        this.game = game;
        this.settings = settings;
        this.opponents = new OpponentRegistry(localAi, chatGpt);
        this.saveFile = saveFile;
    }

    // ---------------------------------------------------------------- состояние

    /** Снимок состояния под тем же монитором, что и изменения партии. */
    public synchronized Map<String, Object> state() {
        return withDefaults(GameView.of(game));
    }

    /** Состояние глазами одного из сетевых игроков: только его рука. */
    public synchronized Map<String, Object> playerState(boolean white) {
        var view = GameView.of(game, white, game.whiteTurn() == white);
        view.put("playerWhite", white);
        return withDefaults(view);
    }

    public synchronized boolean isNetwork() {
        return currentOpponent().network();
    }

    public synchronized boolean chatGptConfigured() { return opponents.chatGptConfigured(); }

    private Map<String, Object> withDefaults(Map<String, Object> view) {
        view.put("defaultMode", settings.defaultMode());
        view.put("newGameDefaults", settings.opponents());
        return view;
    }

    // ---------------------------------------------------------------- новая партия

    /** Создаёт новую партию с возрастающей версией; старые команды становятся недействительны. */
    public synchronized Map<String, Object> newGame(String mode, Integer handLimit, String difficulty) {
        return newGame(mode, handLimit, difficulty, null);
    }

    public synchronized Map<String, Object> newGame(String mode, Integer handLimit, String difficulty, String apiKey) {
        GameMode gameMode = GameMode.fromId(mode == null ? settings.defaultMode() : mode);
        Game next = createGame(settings, gameMode, handLimit, difficulty);
        if (gameMode == GameMode.CHATGPT && apiKey != null && !apiKey.isBlank()) opponents.configureChatGptKey(checkedKey(apiKey));
        replaceGame(next);
        return state();
    }

    /** Полный перезапуск использует настройки режима из конфигурации. */
    public synchronized void reset() {
        newGame(isNetwork() ? GameMode.NETWORK.id() : settings.defaultMode(), null, null);
    }

    /** Партия с настройками режима из конфигурации; явные значения запроса имеют приоритет. */
    private static Game createGame(GameProperties settings, GameMode mode, Integer handLimit, String difficulty) {
        var defaults = settings.forMode(mode.id());
        return new Game(new GameSettings(mode, handLimit == null ? defaults.handLimit() : handLimit,
                Difficulty.fromId(difficulty == null ? defaults.difficulty() : difficulty)), settings.backRank());
    }

    private static String checkedKey(String apiKey) {
        String key = apiKey.trim();
        if (key.length() > 1024 || key.chars().anyMatch(Character::isWhitespace))
            throw new IllegalArgumentException("API-ключ должен быть одной строкой без пробелов");
        return key;
    }

    /** Новая или загруженная партия продолжает нумерацию версий. */
    private void replaceGame(Game next) {
        next.setVersion(game.version() + 1);
        game = next;
    }

    // ---------------------------------------------------------------- сохранение

    /** Сохраняет позицию, включая скрытые карты, только в локальный файл. */
    public synchronized Map<String, Object> save(long version) throws IOException {
        requireVersion(version);
        saveFile.write(game);
        return state();
    }

    /** Полный снимок для скачивания владельцем. */
    public synchronized Map<String, Object> exportGame(long version) {
        requireVersion(version);
        return GameSave.encode(game);
    }

    public synchronized Map<String, Object> importGame(long version, Map<String, Object> data) {
        return importGame(version, data, false);
    }

    /** Подменяет партию только после полного чтения снимка; старые команды отклоняются. */
    public synchronized Map<String, Object> importGame(long version, Map<String, Object> data, boolean networkOnly) {
        requireVersion(version);
        Game restored;
        try {
            restored = GameSave.decode(data);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Файл сохранения повреждён или несовместим.");
        }
        requireAllowedMode(restored, networkOnly);
        replaceGame(restored);
        return state();
    }

    public synchronized Map<String, Object> load(long version) throws IOException {
        return load(version, false);
    }

    public synchronized Map<String, Object> load(long version, boolean networkOnly) throws IOException {
        requireVersion(version);
        Game restored = saveFile.read();
        requireAllowedMode(restored, networkOnly);
        replaceGame(restored);
        return state();
    }

    private static void requireAllowedMode(Game restored, boolean networkOnly) {
        if (networkOnly && restored.mode() != GameMode.NETWORK && restored.mode() != GameMode.CHATGPT)
            throw new IllegalArgumentException("В Railway доступны игра с другом и ChatGPT");
    }

    // ---------------------------------------------------------------- команды игрока

    /** Проверяет версию и очередь перед выполнением действия человека. */
    public synchronized Map<String, Object> play(long version, String actionId) {
        requireVersion(version);
        requireHumanTurn();
        game.play(actionId);
        return state();
    }

    /** Начинает разведку, фиксируя расход карты до раскрытия. */
    public synchronized Map<String, Object> scout(long version, int cardId) {
        requireVersion(version);
        requireHumanTurn();
        game.startScout(cardId);
        return state();
    }

    /** Принимает заявление ничьей от текущего игрока. */
    public synchronized Map<String, Object> claimDraw(long version) {
        requireVersion(version);
        requireHumanTurn();
        game.claimDraw();
        return state();
    }

    /** Сдаться можно и во время чужого хода; сетевую сторону определяет авторизация. */
    public synchronized Map<String, Object> resign(long version, boolean networkWhite) {
        requireVersion(version);
        boolean white = isNetwork() ? networkWhite : game.mode().sharedScreen() ? game.whiteTurn() : true;
        game.resign(white);
        return state();
    }

    // ---------------------------------------------------------------- ход соперника

    /**
     * Запрос соперника не блокирует чтение состояния и создание новой партии:
     * расчёт идёт на копии, а результат применяется, только если версия не изменилась.
     */
    public Map<String, Object> runAi(long version) {
        Game observation;
        Opponent opponent;
        synchronized (this) {
            requireVersion(version);
            if (!currentOpponent().automatic() || game.whiteTurn() || game.result().finished())
                throw new IllegalArgumentException("Сейчас не ход соперника");
            if (opponentRunning) throw new IllegalArgumentException("Соперник уже обдумывает ход");
            opponentRunning = true;
            observation = OpponentTurn.observation(game);
            opponent = currentOpponent();
        }
        try {
            String actionId = opponent.choose(observation);
            synchronized (this) {
                requireVersion(version);
                OpponentTurn.apply(game, opponent, actionId);
                return state();
            }
        } finally {
            synchronized (this) {
                opponentRunning = false;
            }
        }
    }

    // ---------------------------------------------------------------- проверки

    /** Версия может совпасть после перезапуска сервера, поэтому проверяем и саму партию. */
    public synchronized void requireGame(String gameId) {
        if (!game.gameId().equals(gameId)) throw new StalePositionException();
    }

    /** Отклоняет повторные команды и команды от устаревшего состояния. */
    public synchronized void requireVersion(long version) {
        if (version != game.version()) throw new StalePositionException();
    }

    public synchronized void requirePlayerTurn(boolean white) {
        if (game.whiteTurn() != white) throw new IllegalArgumentException("Сейчас ход другого игрока");
    }

    private Opponent currentOpponent() {
        return opponents.forMode(game.mode());
    }

    /** В автоматическом режиме человек управляет только белыми. */
    private void requireHumanTurn() {
        if (currentOpponent().automatic() && !game.whiteTurn())
            throw new IllegalArgumentException("Сейчас ход соперника");
    }

    /**
     * Отдельная ошибка устаревшей версии: API отвечает на неё статусом 409.
     */
    public static final class StalePositionException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        StalePositionException() {
            super("Позиция обновилась. Повторите действие.");
        }
    }
}
