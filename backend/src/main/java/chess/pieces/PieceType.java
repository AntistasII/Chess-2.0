package chess.pieces;

/**
 * Вид шахматной фигуры. Однобуквенный код — внешний формат (JSON, сохранения,
 * запись начального ряда в настройках); внутри движка используется перечисление.
 */
public enum PieceType {
    PAWN('P', "Пешка"),
    KNIGHT('N', "Конь"),
    BISHOP('B', "Слон"),
    ROOK('R', "Ладья"),
    QUEEN('Q', "Ферзь"),
    KING('K', "Король");

    private final char code;
    private final String title;

    PieceType(char code, String title) {
        this.code = code;
        this.title = title;
    }

    /** Код фигуры в английской нотации: P, N, B, R, Q, K. */
    public char code() {
        return code;
    }

    /** Название для истории ходов. */
    public String title() {
        return title;
    }

    /** Пешка может превратиться в эту фигуру. */
    public boolean promotionTarget() {
        return this == KNIGHT || this == BISHOP || this == ROOK || this == QUEEN;
    }

    public static PieceType fromCode(char code) {
        for (PieceType type : values()) if (type.code == code) return type;
        throw new IllegalArgumentException("Неизвестный тип фигуры: " + code);
    }

    /** Разбирает однобуквенную строку (например, вариант превращения в действии). */
    public static PieceType fromCode(String code) {
        if (code == null || code.length() != 1) throw new IllegalArgumentException("Неизвестный тип фигуры: " + code);
        return fromCode(code.charAt(0));
    }
}
