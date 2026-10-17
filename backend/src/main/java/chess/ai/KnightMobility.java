package chess.ai;

import chess.game.Game;

/** Ранняя оценка ловушки: пешечное освобождение выхода полезно ещё до нападения на коня. */
final class KnightMobility {
    private KnightMobility() {}

    static double evaluate(Game game, int square) {
        var knight = game.pieceAt(square);
        int exits = 0;
        // Это оценка текущих атак, не доказательство безопасности всей будущей линии.
        // Геометрия и запретные клетки берутся из правил самой фигуры.
        for (var move : game.movementOptions(square)) {
            var victim = game.pieceAt(move.to());
            if (!game.isSquareAttacked(move.to(), !knight.white())
                    || victim != null && PositionEvaluator.materialValue(victim.type()) >= 320) exits++;
        }
        return exits == 0 ? -90 : exits == 1 ? -20 : 0;
    }
}
