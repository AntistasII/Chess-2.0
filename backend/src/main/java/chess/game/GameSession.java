package chess.game;

import java.util.Map;
import chess.config.GameProperties;
import chess.opponent.Opponent;
import chess.opponent.OpponentRegistry;

/**
 * Владеет локальной партией и последовательно выполняет команды с проверкой версии, независимо от HTTP.
 */
public final class GameSession {
    private Game game;
    private final OpponentRegistry opponents;
    private final GameProperties settings;
    private boolean opponentRunning;

    public GameSession(GameProperties settings, String mode, MoveSelector localAi, MoveSelector chatGpt) {
        this(new Game(mode, settings.forMode(mode).handLimit(), settings.forMode(mode).difficulty(), settings.backRank()),
                settings, localAi, chatGpt);
    }

    /** Позволяет подключить готовую позицию без замены её настроек. */
    public GameSession(Game game, GameProperties settings, MoveSelector localAi, MoveSelector chatGpt) {
        this.game = game;
        this.settings = settings;
        this.opponents = new OpponentRegistry(localAi, chatGpt);
    }

    /**
     * Возвращает снимок состояния под тем же монитором, что и изменения партии.
     */
    public synchronized Map<String, Object> state() {
        return withDefaults(GameView.of(game));
    }

    public synchronized boolean isNetwork() {
        return currentOpponent().network();
    }

    public synchronized Map<String, Object> playerState(boolean white) {
        var view = GameView.of(game, white, game.whiteTurn == white);
        view.put("playerWhite", white);
        return withDefaults(view);
    }

    public synchronized void requirePlayerTurn(boolean white) {
        if (game.whiteTurn != white) throw new IllegalArgumentException("Сейчас ход другого игрока");
    }

    /**
     * Создаёт новую партию с возрастающей версией; старые команды становятся недействительны.
     */
    public synchronized Map<String, Object> newGame(String mode, Integer handLimit, String difficulty) {
        return newGame(mode, handLimit, difficulty, null);
    }

    public synchronized boolean chatGptConfigured() { return opponents.chatGptConfigured(); }

    public synchronized Map<String, Object> newGame(String mode, Integer handLimit, String difficulty, String apiKey) {
        if (mode == null) mode = settings.defaultMode();
        var defaults = settings.forMode(mode);
        Game next = new Game(mode, handLimit == null ? defaults.handLimit() : handLimit,
                difficulty == null ? defaults.difficulty() : difficulty, settings.backRank());
        if (mode.equals("chatgpt") && apiKey != null && !apiKey.isBlank()) {
            String key = apiKey.trim();
            if (key.length() > 1024 || key.chars().anyMatch(Character::isWhitespace))
                throw new IllegalArgumentException("API-ключ должен быть одной строкой без пробелов");
            opponents.configureChatGptKey(key);
        }
        next.version = game.version + 1;
        game = next;
        return state();
    }

    /** Полный перезапуск использует настройки режима из конфигурации. */
    public synchronized void reset() {
        newGame(isNetwork() ? "network" : settings.defaultMode(), null, null);
    }

    private Map<String, Object> withDefaults(Map<String, Object> view) {
        view.put("defaultMode", settings.defaultMode());
        view.put("newGameDefaults", settings.opponents());
        return view;
    }

    /**
     * Сохраняет позицию, включая скрытые карты, только в локальный файл.
     */
    public synchronized Map<String, Object> save(long version) throws java.io.IOException {
        requireVersion(version);
        GameSave.write(game, java.nio.file.Path.of("data/saved-game.json"));
        return state();
    }

    public synchronized Map<String, Object> exportGame(long version) {
        requireVersion(version);
        return GameSave.encode(game);
    }

    public synchronized Map<String, Object> importGame(long version, Map<String, Object> data) {
        return importGame(version, data, false);
    }

    public synchronized Map<String, Object> importGame(long version, Map<String, Object> data, boolean networkOnly) {
        requireVersion(version);
        Game restored;
        try {
            restored = GameSave.decode(data);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Файл сохранения повреждён или несовместим.");
        }
        requireAllowedMode(restored, networkOnly);
        restored.version = game.version + 1;
        game = restored;
        return state();
    }

    /**
     * Подменяет партию только после полного чтения снимка; старые команды отклоняются.
     */
    public synchronized Map<String, Object> load(long version) throws java.io.IOException {
        return load(version, false);
    }

    public synchronized Map<String, Object> load(long version, boolean networkOnly) throws java.io.IOException {
        requireVersion(version);
        Game restored = GameSave.read(java.nio.file.Path.of("data/saved-game.json"));
        requireAllowedMode(restored, networkOnly);
        restored.version = game.version + 1;
        game = restored;
        return state();
    }

    private static void requireAllowedMode(Game restored, boolean networkOnly) {
        if (networkOnly && !restored.mode.equals("network"))
            throw new IllegalArgumentException("На этом сервере доступны только партии на двух игроков со входом по кодам");
    }

    /**
     * Проверяет версию и очередь перед выполнением действия человека.
     */
    public synchronized Map<String, Object> play(long version, String actionId) {
        requireVersion(version);
        requireHumanTurn();
        game.play(actionId);
        return state();
    }

    /**
     * Начинает разведку, фиксируя расход карты до раскрытия.
     */
    public synchronized Map<String, Object> scout(long version, int cardId) {
        requireVersion(version);
        requireHumanTurn();
        game.startScout(cardId);
        return state();
    }

    /**
     * Принимает заявление ничьей от текущего игрока.
     */
    public synchronized Map<String, Object> claimDraw(long version) {
        requireVersion(version);
        requireHumanTurn();
        game.claimDraw();
        return state();
    }

    /** Сдаться можно и во время чужого хода; сетевую сторону определяет авторизация. */
    public synchronized Map<String, Object> resign(long version, boolean networkWhite) {
        requireVersion(version);
        boolean white = isNetwork() ? networkWhite : game.mode.equals("human") ? game.whiteTurn : true;
        game.resign(white);
        return state();
    }

    /**
     * Запрос соперника не блокирует чтение состояния и создание новой партии.
     */
    public Map<String, Object> runAi(long version) {
        Game snapshot;
        Opponent opponent;
        synchronized (this) {
            requireVersion(version);
            if (!currentOpponent().automatic() || game.whiteTurn || !game.result.isEmpty())
                throw new IllegalArgumentException("Сейчас не ход соперника");
            if (opponentRunning) throw new IllegalArgumentException("Соперник уже обдумывает ход");
            opponentRunning = true;
            snapshot = game.copy();
            snapshot.history = new java.util.ArrayList<>(game.history);
            opponent = currentOpponent();
        }
        try {
            // Скрываем чужую руку и колоду до передачи стратегии.
            Game observation = snapshot.observationForCurrentPlayer();
            if (snapshot.scouting >= 0) {
                // Разведка раскрывает только две верхние карты текущему игроку.
                observation.deck.set(0, snapshot.deck.get(0));
                observation.deck.set(1, snapshot.deck.get(1));
            }
            String id = opponent.choose(observation);
            synchronized (this) {
                requireVersion(version);
                Action action = game.legalActions().stream().filter(a -> a.id().equals(id)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Соперник вернул недопустимый ход. Повторите запрос."));
                // ChatGPT сначала расходует разведку, затем решает порядок по раскрытым картам.
                if (opponent.requiresScoutPreview() && game.scouting < 0 && action.kind().equals("card")
                        && game.hand(game.whiteTurn).stream().anyMatch(c -> c.id() == action.cardId()
                        && c.type() == chess.cards.CardType.SCOUT)) {
                    game.startScout(action.cardId());
                } else game.play(id);
                return state();
            }
        } finally {
            synchronized (this) {
                opponentRunning = false;
            }
        }
    }

    /** Версия может совпасть после перезапуска сервера, поэтому проверяем и саму партию. */
    public synchronized void requireGame(String gameId) {
        if (!game.gameId.equals(gameId)) throw new StalePositionException();
    }

    /** Отклоняет повторные команды и команды от устаревшего состояния. */
    public synchronized void requireVersion(long version) {
        if (version != game.version) throw new StalePositionException();
    }

    private Opponent currentOpponent() {
        return opponents.forMode(game.mode);
    }

    /** В автоматическом режиме человек управляет только белыми. */
    private void requireHumanTurn() {
        if (currentOpponent().automatic() && !game.whiteTurn)
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
