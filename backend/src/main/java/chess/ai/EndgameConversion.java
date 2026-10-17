package chess.ai;

import chess.game.Game;

/** Реализация перевеса тяжёлыми фигурами: ограничивать короля, подводить своего, не ждать карт. */
final class EndgameConversion {
    private EndgameConversion() {}

    static boolean active(Game game, boolean side) {
        int own = 0, enemy = 0;
        boolean heavy = false;
        for (int s = 0; s < game.boardGeometry().size(); s++) {
            var p = game.pieceAt(s);
            if (p == null) continue;
            if (p.white() == side) {
                own += PositionEvaluator.materialValue(p.type());
                if (!p.astral() && (p.type() == 'Q' || p.type() == 'R')) heavy = true;
            } else enemy += PositionEvaluator.materialValue(p.type());
        }
        return heavy && enemy <= 500 && own - enemy >= 450;
    }

    static double evaluate(Game game, boolean side) {
        if (!active(game, side)) return 0;
        int ownKing = -1, enemyKing = -1;
        for (int s = 0; s < game.boardGeometry().size(); s++) {
            var p = game.pieceAt(s);
            if (p != null && p.type() == 'K') {
                if (p.white() == side) ownKing = s; else enemyKing = s;
            }
        }
        if (ownKing < 0 || enemyKing < 0) return 0;
        var geo = game.boardGeometry();
        int king = enemyKing;
        long exits = game.movementOptions(king).stream()
                .filter(a -> !game.isSquareAttacked(a.to(), side)).count();
        int edge = Math.min(Math.min(geo.file(king), geo.width()-1-geo.file(king)),
                Math.min(geo.rank(king), geo.height()-1-geo.rank(king)));
        int distance = Math.max(Math.abs(geo.file(ownKing)-geo.file(king)),Math.abs(geo.rank(ownKing)-geo.rank(king)));
        // Геометрическая оценка выхода консервативна: точную законность и пат проверяет поиск.
        return (8-Math.min(8,exits))*12 - edge*18 - distance*8;
    }
}
