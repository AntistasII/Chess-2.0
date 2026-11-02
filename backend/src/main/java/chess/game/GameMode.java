package chess.game;

/**
 * Против кого идёт партия. Значение id используется в настройках, JSON и сохранениях.
 */
public enum GameMode {
    /** Локальный ИИ играет чёрными. */
    AI("ai"),
    /** Двое людей за одним устройством. */
    HUMAN("human"),
    /** Чёрными ходит ChatGPT. */
    CHATGPT("chatgpt"),
    /** Второй игрок подключается по сети. */
    NETWORK("network");

    private final String id;

    GameMode(String id) { this.id = id; }

    public String id() { return id; }

    /** Обе стороны управляются с одного устройства, поэтому видна рука ходящего игрока. */
    public boolean sharedScreen() { return this == HUMAN; }

    public static GameMode fromId(String id) {
        for (GameMode mode : values()) if (mode.id.equals(id)) return mode;
        throw new IllegalArgumentException("Неизвестный режим");
    }
}
