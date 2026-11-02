package chess.pieces;

/**
 * Ладья. Общие эффекты и состояние наследуются от Piece.
 */
public final class Rook extends SlidingPiece {
    public Rook(int id, boolean white) {
        super(id, white);
    }

    @Override
    public PieceType type() {
        return PieceType.ROOK;
    }
    @Override
    protected boolean supportsDirection(int dr, int df) { return dr == 0 || df == 0; }
}
