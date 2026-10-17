package chess.pieces;

import chess.board.Geometry;

/**
 * Данные позиции для фигур. Методы чтения не должны изменять доску или фигуры.
 */
public interface MovementContext {
    Geometry geometry();

    Piece pieceAt(int square);

    default boolean blocked(int square) { return false; }

    int pawnStartRank(boolean white);

    int enPassant();

    int kingStartSquare(boolean white);

    int rookStartSquare(boolean white, boolean shortSide);

    boolean castleAllowed(boolean white, boolean shortSide);

    boolean kingSafeAt(King king, int from, int to);

    default boolean canCapture(int square, boolean white) {
        Piece target = pieceAt(square);
        return !blocked(square) && target != null && !target.astral() && target.white() != white && target.type() != 'K';
    }

    default boolean available(int square, boolean white) {
        return !blocked(square) && (pieceAt(square) == null || canCapture(square, white));
    }

    /**
     * Промежуточные клетки должны быть пусты; астральные фигуры тоже перекрывают луч.
     */
    default boolean clearRay(int from, int to) {
        var geometry = geometry();
        int dr = Integer.signum(geometry.rank(to) - geometry.rank(from));
        int df = Integer.signum(geometry.file(to) - geometry.file(from));
        int rank = geometry.rank(from) + dr, file = geometry.file(from) + df;
        while (rank != geometry.rank(to) || file != geometry.file(to)) {
            if (!geometry.inside(rank, file) || (pieceAt(geometry.index(rank, file)) != null || blocked(geometry.index(rank, file)))) return false;
            rank += dr;
            file += df;
        }
        return true;
    }
}
