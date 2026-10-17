package chess.pieces;

import chess.board.Geometry;
import chess.game.Action;

import java.util.List;

/**
 * Король: короткие лучи, безопасный промежуточный шаг усиления и рокировка.
 */
public final class King extends SlidingPiece {
    public King(int id, boolean white) {
        super(id, white);
    }

    @Override
    public char type() {
        return 'K';
    }

    @Override
    protected boolean supportsDirection(int dr, int df) {
        return true;
    }

    @Override
    protected int maxDistance(Geometry geometry) {
        return boost() ? 2 : 1;
    }

    @Override
    protected boolean safePath(MovementContext context, int from, int dr, int df, int distance) {
        if (distance == 1) return true;
        var geometry = context.geometry();
        int intermediate = geometry.index(geometry.rank(from) + dr, geometry.file(from) + df);
        return context.kingSafeAt(this, from, intermediate);
    }

    @Override
    public List<Action> moves(MovementContext context, int from) {
        var moves = super.moves(context, from);
        if (from != context.kingStartSquare(white()) || moved() || !context.kingSafeAt(this, from, from))
            return moves;
        for (boolean shortSide : new boolean[]{true, false}) {
            int rookSquare = context.rookStartSquare(white(), shortSide);
            Piece rook = context.pieceAt(rookSquare);
            if (!context.castleAllowed(white(), shortSide) || rook == null || rook.type() != 'R'
                    || rook.white() != white() || rook.moved() || rook.astral() || rook.frozen()) continue;
            int step = shortSide ? 1 : -1;
            if (context.clearRay(from, rookSquare) && context.kingSafeAt(this, from, from + step))
                moves.add(Action.move(from, from + step * 2, "castle"));
        }
        return moves;
    }
}
