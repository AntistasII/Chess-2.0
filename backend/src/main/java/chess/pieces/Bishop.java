package chess.pieces;

/**
 * Слон. Общие эффекты и состояние наследуются от Piece.
 */
public final class Bishop extends SlidingPiece {
    public Bishop(int id, boolean white) {
        super(id, white);
    }

    @Override
    public char type() {
        return 'B';
    }

    @Override
    protected boolean supportsDirection(int dr, int df) {
        return dr != 0 && df != 0;
    }
}
