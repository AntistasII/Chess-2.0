package chess.pieces;

/**
 * Ладья. Общие эффекты и состояние наследуются от Piece.
 */
public final class Rook extends SlidingPiece {
    public Rook(int id, boolean white) {
        super(id, white);
    }

    @Override
    public char type() {
        return 'R';
    }
    @Override
    protected boolean supportsDirection(int dr, int df) { return dr == 0 || df == 0; }
}
