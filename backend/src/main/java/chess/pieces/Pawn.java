package chess.pieces;

import chess.game.Action;

import java.util.ArrayList;
import java.util.List;

/**
 * Пешка. Общие эффекты и состояние наследуются от Piece.
 */
public final class Pawn extends Piece {
    public Pawn(int id, boolean white) {
        super(id, white);
    }

    @Override
    public char type() {
        return 'P';
    }

    @Override
    public List<Action> moves(MovementContext context, int from) {
        var moves = new ArrayList<Action>();
        var geometry = context.geometry();
        int rank = geometry.rank(from), file = geometry.file(from), direction = white() ? 1 : -1;
        boolean firstMove = !moved() && rank == context.pawnStartRank(white());
        int max = (firstMove ? 2 : 1) + (boost() ? 1 : 0);
        for (int distance = 1; distance <= max; distance++) {
            int nextRank = rank + direction * distance;
            if (!geometry.inside(nextRank, file)) break;
            int to = geometry.index(nextRank, file);
            if (context.pieceAt(to) != null || context.blocked(to)) break;
            addMove(moves, context, from, to);
        }
        for (int df : new int[]{-1, 1}) {
            if (!geometry.inside(rank + direction, file + df)) continue;
            int to = geometry.index(rank + direction, file + df);
            if (context.canCapture(to, white()) || canTakeEnPassant(context, to, direction))
                addMove(moves, context, from, to);
        }
        return moves;
    }

    private boolean canTakeEnPassant(MovementContext context, int to, int direction) {
        if (context.blocked(to) || to != context.enPassant() || context.pieceAt(to) != null) return false;
        Piece victim = context.pieceAt(to - direction * context.geometry().width());
        return victim != null && victim.type() == 'P' && victim.white() != white() && !victim.astral();
    }

    private void addMove(List<Action> moves, MovementContext context, int from, int to) {
        int rank = context.geometry().rank(to);
        if (rank == 0 || rank == context.geometry().height() - 1)
            for (String type : List.of("Q", "R", "B", "N")) moves.add(Action.move(from, to, type));
        else moves.add(Action.move(from, to, ""));
    }

    @Override
    public boolean attacks(MovementContext context, int from, int target) {
        var geometry = context.geometry();
        return geometry.rank(target) - geometry.rank(from) == (white() ? 1 : -1)
                && Math.abs(geometry.file(target) - geometry.file(from)) == 1;
    }

    /**
     * Превращение заменяет пешку новой фигурой, сохраняя её состояние.
     */
    public Piece promoteTo(char type) {
        if (type != 'Q' && type != 'R' && type != 'B' && type != 'N')
            throw new IllegalArgumentException("Пешку можно превратить в ферзя, ладью, слона или коня");
        return copyAs(type);
    }
}
