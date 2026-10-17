package chess.pieces;

/**
 * Создаёт конкретную фигуру по коду из начальной расстановки или сохранения.
 */
public final class PieceFactory {
    private PieceFactory() {
    }

    public static Piece create(int id, char type, boolean white) {
        return switch (type) {
            case 'P' -> new Pawn(id, white);
            case 'N' -> new Knight(id, white);
            case 'B' -> new Bishop(id, white);
            case 'R' -> new Rook(id, white);
            case 'Q' -> new Queen(id, white);
            case 'K' -> new King(id, white);
            default -> throw new IllegalArgumentException("Неизвестный тип фигуры: " + type);
        };
    }
}
