package chess.pieces;

/**
 * Создаёт конкретную фигуру по виду или по однобуквенному коду из начальной расстановки и сохранения.
 */
public final class PieceFactory {
    private PieceFactory() {
    }

    public static Piece create(int id, PieceType type, boolean white) {
        return switch (type) {
            case PAWN -> new Pawn(id, white);
            case KNIGHT -> new Knight(id, white);
            case BISHOP -> new Bishop(id, white);
            case ROOK -> new Rook(id, white);
            case QUEEN -> new Queen(id, white);
            case KING -> new King(id, white);
        };
    }

    /** Код P/N/B/R/Q/K — внешний формат расстановки и сохранений. */
    public static Piece create(int id, char code, boolean white) {
        return create(id, PieceType.fromCode(code), white);
    }
}
