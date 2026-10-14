package chess.game;

import java.util.Map;

/**
 * Владеет локальной партией и последовательно выполняет команды с проверкой версии, независимо от HTTP.
 */
public final class GameSession {
    private Game game;
    private final MoveSelector opponent;
    private final MoveSelector chatGpt;
    private boolean opponentRunning;

    public GameSession(MoveSelector opponent, MoveSelector chatGpt) {
        this(new Game("ai", 3, "normal"), opponent, chatGpt);
    }

    /**
     * Создаёт сессию и подключает выбранную реализацию компьютерного соперника.
     */
    public GameSession(MoveSelector opponent) {
        this(new Game("ai", 3, "normal"), opponent);
    }

    /**
     * Конструктор с готовой партией для сценариев и проверок.
     */
    GameSession(Game game, MoveSelector opponent) {
        this(game, opponent, position -> { throw new IllegalArgumentException("ChatGPT не подключён к серверу"); });
    }

    GameSession(Game game, MoveSelector opponent, MoveSelector chatGpt) {
        this.game = game;
        this.chatGpt = java.util.Objects.requireNonNull(chatGpt);
        this.opponent = java.util.Objects.requireNonNull(opponent);
    }

    /**
     * Возвращает снимок состояния под тем же монитором, что и изменения партии.
     */
    public synchronized Map<String, Object> state() {
        return GameView.of(game);
    }

    /**
     * Создаёт новую партию с возрастающей версией; старые команды становятся недействительны.
     */
    public synchronized Map<String, Object> newGame(String mode, int handLimit, String difficulty) {
        Game next = new Game(mode, handLimit, difficulty);
        next.version = game.version + 1;
        game = next;
        return state();
    }

    /** Сохраняет позицию, включая скрытые карты, только в локальный файл. */
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
        requireVersion(version);
        Game restored;
        try { restored = GameSave.decode(data); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Файл сохранения повреждён или несовместим."); }
        restored.version = game.version + 1;
        game = restored;
        return state();
    }

    /** Подменяет партию только после полного чтения снимка; старые команды отклоняются. */
    public synchronized Map<String, Object> load(long version) throws java.io.IOException {
        requireVersion(version);
        Game restored = GameSave.read(java.nio.file.Path.of("data/saved-game.json"));
        restored.version = game.version + 1;
        game = restored;
        return state();
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

    /** Запрос соперника не блокирует чтение состояния и создание новой партии. */
    public Map<String, Object> runAi(long version) {
        Game snapshot;
        MoveSelector selector;
        synchronized (this) {
            requireVersion(version);
            if (game.mode.equals("human") || game.whiteTurn || !game.result.isEmpty())
                throw new IllegalArgumentException("Сейчас не ход соперника");
            if (opponentRunning) throw new IllegalArgumentException("Соперник уже обдумывает ход");
            opponentRunning = true;
            snapshot = game.copy();
            snapshot.history = new java.util.ArrayList<>(game.history);
            selector = game.mode.equals("chatgpt") ? chatGpt : opponent;
        }
        try {
            // Скрываем чужую руку и колоду до передачи стратегии.
            Game observation = snapshot.observationForCurrentPlayer();
            if (snapshot.scouting >= 0) {
                // Разведка раскрывает только две верхние карты текущему игроку.
                observation.deck.set(0, snapshot.deck.get(0));
                observation.deck.set(1, snapshot.deck.get(1));
            }
            String id = selector.choose(observation);
            synchronized (this) {
                requireVersion(version);
                Action action = game.legalActions().stream().filter(a -> a.id().equals(id)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Соперник вернул недопустимый ход. Повторите запрос."));
                // ChatGPT сначала расходует разведку, затем решает порядок по раскрытым картам.
                if (game.mode.equals("chatgpt") && game.scouting < 0 && action.kind().equals("card")
                        && game.hand(game.whiteTurn).stream().anyMatch(c -> c.id() == action.cardId()
                            && c.type() == chess.cards.CardType.SCOUT)) {
                    game.startScout(action.cardId());
                } else game.play(id);
                return state();
            }
        } finally {
            synchronized (this) { opponentRunning = false; }
        }
    }

    /**
     * Отклоняет повторные команды и команды от устаревшего состояния.
     */
    public synchronized void requireVersion(long version) {
        if (version != game.version) throw new StalePositionException();
    }

    /**
     * В режиме ИИ человек управляет только белыми.
     */
    private void requireHumanTurn() {
        if (!game.mode.equals("human") && !game.whiteTurn) throw new IllegalArgumentException("Сейчас ход соперника");
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
