package chess.pieces;

/**
 * Ферзь. Общие эффекты и состояние наследуются от Piece.
 */
public final class Queen extends SlidingPiece {
    public Queen(int id, boolean white) {
        super(id, white);
    }

    @Override
    public PieceType type() {
        return PieceType.QUEEN;
    }
    @Override
    protected boolean supportsDirection(int dr, int df) { return true; }
}
