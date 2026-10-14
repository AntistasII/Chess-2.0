package chess.ai;

import chess.board.Board;
import chess.game.Game;

/** Оценивает фигуры, эффекты и карты для выбора действия локальным ИИ. */
final class PositionEvaluator {
    private PositionEvaluator() {}

    /** Положительная оценка означает преимущество выбранной стороны. */
    static double evaluate(Game g, boolean side) {
        double score = 0;
        Board.Geometry board = g.boardGeometry();
        for (int s = 0; s < board.size(); s++) {
            var p = g.pieceAt(s);
            if (p == null) continue;
            double value = switch (p.type()) {
                case 'P' -> 100;
                case 'N' -> 320;
                case 'B' -> 335;
                case 'R' -> 500;
                case 'Q' -> 900;
                default -> 0;
            };
            int advancement = p.white() ? board.rank(s) : board.height() - 1 - board.rank(s);
            // Нормализация сохраняет прежнюю оценку на 8×8 и работает на прямоугольнике.
            double center = 7 - Math.abs((board.width() - 1) / 2.0 - board.file(s)) * 7 / Math.max(1, board.width() - 1)
                    - Math.abs((board.height() - 1) / 2.0 - board.rank(s)) * 7 / Math.max(1, board.height() - 1);
            if (p.type() == 'P') value += advancement * 9;
            if (p.type() == 'N' || p.type() == 'B') value += center * 7;
            if (p.boost()) value += p.type() == 'K' ? 15 : 22;
            if (p.shield()) value += 18;
            if (p.frozen()) value -= value * .07;
            if (p.astral()) value -= value * .12;
            score += (p.white() == side ? 1 : -1) * value;
        }
        for (boolean white : new boolean[]{true, false}) {
            double handValue = 0;
            for (var c : g.cards(white))
                handValue += switch (c.type()) {
                    case CONVERT -> 90;
                    case ASTRAL -> 65;
                    case FREEZE -> 45;
                    case SHIELD -> 28;
                    case PAWN_BOOST -> 25;
                    case KING_BOOST -> 18;
                    case REVIVE_PAWN -> g.capturedPieces().stream().anyMatch(p -> p.white() == white && p.type() == 'P') ? 65 : 12;
                    case REVIVE_PIECE ->
                            g.capturedPieces().stream().filter(p -> p.white() == white && p.type() != 'P').mapToInt(p -> p.type() == 'Q' ? 350 : p.type() == 'R' ? 230 : 140).max().orElse(12);
                    case CLEANSE -> 30;
                    case SWAP -> 38;
                    case SCOUT -> 10;
                    case EXCHANGE -> 15;
                    default -> 45;
                };
            score += (white == side ? 1 : -1) * handValue;
        }
        if (g.inCheck(side)) score -= 75;
        if (g.inCheck(!side)) score += 75;
        return score;
    }
}
