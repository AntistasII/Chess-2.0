package chess.ai;

import chess.game.Game;

/**
 * Быстрый 64-битный отпечаток позиции для порядка ходов в поиске. Не учитывает
 * все детали (длительность эффектов, поля заклинаний), поэтому используется только
 * для выбора первого кандидата, но не для подмены оценок.
 */
final class PositionHash {
    private PositionHash() {}

    static long of(Game game) {
        var geometry = game.boardGeometry();
        long h = mix(geometry.width() * 1_000L + geometry.height() + (game.whiteTurn() ? 1_000_000L : 0));
        for (int square = 0; square < geometry.size(); square++) {
            var p = game.pieceAt(square);
            if (p == null) continue;
            long code = p.type().ordinal() * 2L + (p.white() ? 1 : 0);
            code = code * 16 + (p.frozen() ? 1 : 0) + (p.astral() ? 2 : 0) + (p.shield() ? 4 : 0) + (p.boost() ? 8 : 0);
            h ^= mix(square * 4096L + code + 7);
        }
        int castling = (game.hasCastlingRight(true, true) ? 1 : 0) | (game.hasCastlingRight(true, false) ? 2 : 0)
                | (game.hasCastlingRight(false, true) ? 4 : 0) | (game.hasCastlingRight(false, false) ? 8 : 0);
        h ^= mix(castling + 99_991L);
        for (boolean white : new boolean[]{true, false}) {
            long hand = white ? 17 : 31;
            for (var card : game.cards(white)) hand += mix(card.type().ordinal() + (white ? 500L : 900L));
            h ^= mix(hand);
        }
        return h;
    }

    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
