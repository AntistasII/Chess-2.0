package chess.game;

/** Сложность локального ИИ; в партиях с человеком правила не меняет. */
public enum Difficulty {
    EASY("easy"), NORMAL("normal"), HARD("hard");

    private final String id;

    Difficulty(String id) { this.id = id; }

    public String id() { return id; }

    public static Difficulty fromId(String id) {
        for (Difficulty difficulty : values()) if (difficulty.id.equals(id)) return difficulty;
        throw new IllegalArgumentException("Неизвестная сложность");
    }
}
