package chess.pieces;

import chess.board.Geometry;
import chess.game.Action;
import java.util.ArrayList;
import java.util.List;

/** Общий обход лучей для слона, ладьи, ферзя и короля с ограниченной дальностью. */
public abstract class SlidingPiece extends Piece {
    protected SlidingPiece(int id, boolean white) { super(id, white); }

    protected abstract boolean supportsDirection(int dr, int df);

    protected int maxDistance(Geometry geometry) { return Math.max(geometry.width(), geometry.height()) - 1; }

    protected boolean safePath(MovementContext context, int from, int dr, int df, int distance) { return true; }

    @Override public List<Action> moves(MovementContext context, int from) {
        var moves = new ArrayList<Action>();
        var geometry = context.geometry();
        int rank = geometry.rank(from), file = geometry.file(from);
        for (int dr = -1; dr <= 1; dr++)
            for (int df = -1; df <= 1; df++) {
                if (dr == 0 && df == 0 || !supportsDirection(dr, df)) continue;
                for (int distance = 1; distance <= maxDistance(geometry); distance++) {
                    int nr = rank + dr * distance, nf = file + df * distance;
                    if (!geometry.inside(nr, nf)) break;
                    int to = geometry.index(nr, nf);
                    if (!context.available(to, white())) break;
                    if (safePath(context, from, dr, df, distance)) moves.add(Action.move(from, to, ""));
                    if (context.pieceAt(to) != null) break;
                }
            }
        return moves;
    }

    @Override public boolean attacks(MovementContext context, int from, int target) {
        var geometry = context.geometry();
        int dr = geometry.rank(target) - geometry.rank(from), df = geometry.file(target) - geometry.file(from);
        int distance = Math.max(Math.abs(dr), Math.abs(df));
        boolean straight = dr == 0 || df == 0 || Math.abs(dr) == Math.abs(df);
        return distance > 0 && straight && distance <= maxDistance(geometry)
                && supportsDirection(Integer.signum(dr), Integer.signum(df)) && context.clearAttackRay(from, target);
    }
}
