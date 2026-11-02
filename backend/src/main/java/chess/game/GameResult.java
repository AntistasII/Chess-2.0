package chess.game;

/**
 * Итог партии. Значение id передаётся интерфейсу и записывается в сохранение;
 * пустая строка означает, что партия продолжается.
 */
public enum GameResult {
    IN_PROGRESS(""),
    /** Мат: победили белые. */
    WHITE_WON("white"),
    /** Мат: победили чёрные. */
    BLACK_WON("black"),
    STALEMATE("stalemate"),
    WHITE_RESIGNED("whiteResigned"),
    BLACK_RESIGNED("blackResigned"),
    /** Автоматическая ничья после 150 действий без хода пешкой и взятия. */
    SEVENTY_FIVE_MOVES("seventyFiveMoves"),
    /** Автоматическая ничья при пятом повторении позиции. */
    REPETITION("repetition"),
    CLAIMED_DRAW("claimedDraw");

    private final String id;

    GameResult(String id) { this.id = id; }

    public String id() { return id; }

    public boolean finished() { return this != IN_PROGRESS; }

    static GameResult checkmate(boolean winnerWhite) { return winnerWhite ? WHITE_WON : BLACK_WON; }

    static GameResult resignation(boolean resigningWhite) { return resigningWhite ? WHITE_RESIGNED : BLACK_RESIGNED; }

    public static GameResult fromId(String id) {
        for (GameResult result : values()) if (result.id.equals(id)) return result;
        throw new IllegalArgumentException("Неизвестный итог партии: " + id);
    }
}
