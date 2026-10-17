package chess.pieces;

/**
 * Ферзь. Общие эффекты и состояние наследуются от Piece.
 */
public final class Queen extends SlidingPiece {
    public Queen(int id, boolean white) {
        super(id, white);
    }

    @Override
    public char type() {
        return 'Q';
    }
    @Override
    protected boolean supportsDirection(int dr, int df) { return true; }
}
