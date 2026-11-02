package chess.legacyai;

import chess.board.Geometry;
import chess.game.Game;

/** Оценивает фигуры, эффекты и карты для выбора действия локальным ИИ. */
final class PositionEvaluator {
    private PositionEvaluator() {}

    static int materialValue(chess.pieces.PieceType type) {
        return switch (type) {
            case PAWN -> 100;
            case KNIGHT -> 320;
            case BISHOP -> 335;
            case ROOK -> 500;
            case QUEEN -> 900;
            default -> 0;
        };
    }

    /** Положительная оценка означает преимущество выбранной стороны. */
    static double evaluate(Game g, boolean side) {
        double score = 0;
        Geometry board = g.boardGeometry();
        for (int s = 0; s < board.size(); s++) {
            var p = g.pieceAt(s);
            if (p == null) continue;
            double value = materialValue(p.type());
            int advancement = p.white() ? board.rank(s) : board.height() - 1 - board.rank(s);
            // Нормализация сохраняет прежнюю оценку на 8×8 и работает на прямоугольнике.
            double center = 7 - Math.abs((board.width() - 1) / 2.0 - board.file(s)) * 7 / Math.max(1, board.width() - 1)
                    - Math.abs((board.height() - 1) / 2.0 - board.rank(s)) * 7 / Math.max(1, board.height() - 1);
            if (p.type() == chess.pieces.PieceType.PAWN) value += advancement * 9;
            if (p.type() == chess.pieces.PieceType.KNIGHT && !p.frozen() && !p.astral()) value += KnightMobility.evaluate(g, s);
            if (p.type() == chess.pieces.PieceType.KNIGHT || p.type() == chess.pieces.PieceType.BISHOP) value += center * 7;
            if (p.boost()) value += p.type() == chess.pieces.PieceType.KING ? 15 : 22;
            if (p.shield()) value += 18;
            if (p.frozen()) value -= value * .18;
            if (p.astral()) value -= value * .25;
            // Не оставляем фигуры под бесплатным взятием, даже на границе глубины поиска.
            if (p.type() != chess.pieces.PieceType.KING && !p.astral() && g.isSquareAttacked(s, !p.white())) {
                boolean defended = g.isSquareAttacked(s, p.white());
                value -= materialValue(p.type()) * (defended ? .06 : .45);
            }
            if (p.type() == chess.pieces.PieceType.KING) {
                // Укрытие пешками полезно при наличии тяжёлых фигур у противника.
                for (int df = -1; df <= 1; df++) {
                    int rank = board.rank(s) + (p.white() ? 1 : -1), file = board.file(s) + df;
                    if (!board.inside(rank, file)) continue;
                    var shelter = g.pieceAt(board.index(rank, file));
                    if (shelter != null && shelter.white() == p.white() && shelter.type() == chess.pieces.PieceType.PAWN) value += 10;
                }
            }
            if (p.type() == chess.pieces.PieceType.KING) value += KingSafety.evaluate(g, p, s);
            score += (p.white() == side ? 1 : -1) * value;
        }
        for (boolean white : new boolean[]{true, false}) {
            double handValue = 0;
            for (var c : g.cards(white))
                handValue += switch (c.type()) {
                    case CONVERT -> 90;
                    case UNDO_MOVE -> 70;
                    case DESTROY_CARD -> g.cards(!white).isEmpty() ? 5 : 55;
                    case TRAP, HIDDEN_TRAP -> 35;
                    case WHITE_HOLE -> 45;
                    case SACRIFICE -> 12;
                    case EXPAND_BOTTOM, EXPAND_TOP, EXPAND_LEFT, EXPAND_RIGHT -> 15;
                    case ASTRAL -> 40;
                    case FREEZE -> 25;
                    case SHIELD -> 28;
                    case PAWN_BOOST -> 25;
                    case KING_BOOST -> 18;
                    case REVIVE_PAWN -> g.capturedPieces().stream().anyMatch(p -> p.white() == white && p.type() == chess.pieces.PieceType.PAWN) ? 65 : 12;
                    case REVIVE_PIECE ->
                            g.capturedPieces().stream().filter(p -> p.white() == white && p.type() != chess.pieces.PieceType.PAWN).mapToInt(p -> p.type() == chess.pieces.PieceType.QUEEN ? 350 : p.type() == chess.pieces.PieceType.ROOK ? 230 : 140).max().orElse(12);
                    case CLEANSE -> 30;
                    case SWAP -> 38;
                    case SCOUT -> 10;
                    case EXCHANGE -> 15;
                    default -> 45;
                };
            score += (white == side ? 1 : -1) * handValue;
        }
        score += EndgameConversion.evaluate(g, side) - EndgameConversion.evaluate(g, !side);
        if (g.inCheck(side)) score -= 75;
        if (g.inCheck(!side)) score += EndgameConversion.active(g, side) ? 25 : 75;
        return score;
    }
}
