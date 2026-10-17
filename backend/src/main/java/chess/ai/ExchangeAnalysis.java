package chess.ai;

import chess.game.Game;

/** Продолжает размен на одной клетке, включая вскрывающиеся линии и легальность взятий. */
final class ExchangeAnalysis {
    private ExchangeAnalysis() {}

    static int recover(Game game, boolean side, int baseline, int square) {
        int best = TacticalHints.material(game, side) - baseline;
        for (var action : game.materialRecoveryActions(square)) {
            if (action.kind().equals("move") && action.to() == square)
                best = Math.max(best, settle(game.simulate(action), side, baseline, square, 4));
            else if (action.kind().equals("card"))
                best = Math.max(best, TacticalHints.material(game.simulate(action), side) - baseline);
        }
        return best;
    }

    private static int settle(Game game, boolean side, int baseline, int square, int depth) {
        int best = TacticalHints.material(game, side) - baseline;
        if (depth == 0 || game.pieceAt(square) == null) return best;
        boolean maximize = game.whiteTurn() == side;
        // Размен можно прекратить; рассматриваются лишь законные взятия на его клетке.
        for (var action : game.legalRecaptures(square)) {
            if (!action.kind().equals("move") || action.to() != square) continue;
            int score = settle(game.simulate(action), side, baseline, square, depth - 1);
            best = maximize ? Math.max(best, score) : Math.min(best, score);
        }
        return best;
    }
}
