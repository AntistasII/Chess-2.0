package chess.ai;

import chess.game.Game;
import chess.pieces.Piece;

/** Укрытие и рокировка важны при тяжёлых фигурах; в окончании король свободен действовать. */
final class KingSafety {
    private KingSafety() {}

    static int attackingMaterial(Game game, boolean side) {
        int material = 0;
        for (int square = 0; square < game.boardGeometry().size(); square++) {
            Piece p = game.pieceAt(square);
            if (p != null && p.white() == side && (p.type() == 'Q' || p.type() == 'R'))
                material += PositionEvaluator.materialValue(p.type());
        }
        return material;
    }

    static double evaluate(Game game, Piece king, int square) {
        double danger = Math.min(1, attackingMaterial(game, !king.white()) / 1500.0);
        if (danger == 0) return 0;
        boolean shortRight = game.hasCastlingRight(king.white(), true), longRight = game.hasCastlingRight(king.white(), false);
        double value = shortRight || longRight ? 55 + (shortRight && longRight ? 15 : 0) : 0;
        var geometry = game.boardGeometry();
        int home = geometry.originRank() + (king.white() ? 0 : 7);
        int advance = Math.abs(geometry.rank(square) - home);
        int file = geometry.file(square) - geometry.originFile();
        value -= Math.min(3, advance) * 45;
        if (advance == 0 && (file == 3 || file == 4)) value -= 25;
        if (advance == 0 && king.moved() && (file == 2 || file == 6)) value += 60;
        return value * danger;
    }
}
