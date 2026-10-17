package chess.pieces;

import chess.game.Action;

import java.util.ArrayList;
import java.util.List;

/**
 * Конь. Общие эффекты и состояние наследуются от Piece.
 */
public final class Knight extends Piece {
    public Knight(int id, boolean white) {
        super(id, white);
    }

    @Override
    public char type() {
        return 'N';
    }

    private static final int[][] OFFSETS = {{1, 2}, {1, -2}, {-1, 2}, {-1, -2},
            {2, 1}, {2, -1}, {-2, 1}, {-2, -1}};

    @Override
    public List<Action> moves(MovementContext context, int from) {
        var moves = new ArrayList<Action>();
        var geometry = context.geometry();
        for (int[] offset : OFFSETS) {
            int rank = geometry.rank(from) + offset[0], file = geometry.file(from) + offset[1];
            if (!geometry.inside(rank, file)) continue;
            int to = geometry.index(rank, file);
            if (context.available(to, white())) moves.add(Action.move(from, to, ""));
        }
        return moves;
    }

    @Override
    public boolean attacks(MovementContext context, int from, int target) {
        return reaches(context.geometry(), from, target);
    }

    public static boolean reaches(chess.board.Geometry geometry, int from, int target) {
        int dr = Math.abs(geometry.rank(target) - geometry.rank(from));
        int df = Math.abs(geometry.file(target) - geometry.file(from));
        return dr * df == 2;
    }
}
