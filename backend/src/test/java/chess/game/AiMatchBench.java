package chess.game;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Ручной стенд: партии нового ИИ против прежнего. Запуск:
 * mvn test -Dtest=AiMatchBench -Dbench=true [-Dbench.games=10 -Dbench.level=normal]
 */
class AiMatchBench {
    @Test void match() {
        Assumptions.assumeTrue(Boolean.getBoolean("bench"));
        int games = Integer.getInteger("bench.games", 6);
        String level = System.getProperty("bench.level", "normal");
        int maxPlies = Integer.getInteger("bench.plies", 160);
        var props = TestSessions.defaults();
        MoveSelector fresh = new chess.ai.Ai(props);
        var oldLimits = java.util.Map.of(
                "easy", new chess.config.GameProperties.SearchLimits(2, 250, 500),
                "normal", new chess.config.GameProperties.SearchLimits(4, 1400, 1500),
                "hard", new chess.config.GameProperties.SearchLimits(5, 4500, 3000));
        var oldProps = new chess.config.GameProperties(props.backRank(), props.defaultMode(), props.opponents(), oldLimits);
        MoveSelector legacy = Boolean.getBoolean("bench.selfPlay") ? new chess.ai.Ai(props)
                : new chess.legacyai.Ai(Boolean.getBoolean("bench.sameLimits") ? props : oldProps);
        double score = 0;
        long freshTime = 0, legacyTime = 0;
        int freshMoves = 0, legacyMoves = 0;
        for (int g = 0; g < games; g++) {
            boolean freshWhite = g % 2 == 0;
            Game game = TestSessions.game("ai", 3, level);
            String oldLevel = System.getProperty("bench.oldLevel", level);
            int plies = 0;
            while (game.result.isEmpty() && plies < maxPlies) {
                boolean freshTurn = game.whiteTurn() == freshWhite;
                long t = System.nanoTime();
                String id;
                try {
                    if (freshTurn) id = fresh.choose(game);
                    else {
                        // Уровень соперника задаётся отдельно, если нужно сравнить разные уровни.
                        game.difficulty = oldLevel;
                        id = legacy.choose(game);
                        game.difficulty = level;
                    }
                } catch (RuntimeException e) {
                    System.out.println("  exception in " + (freshTurn ? "new" : "old") + ": " + e);
                    game.result = freshTurn ? (freshWhite ? "black" : "white") : (freshWhite ? "white" : "black");
                    break;
                }
                long dt = System.nanoTime() - t;
                if (freshTurn) { freshTime += dt; freshMoves++; } else { legacyTime += dt; legacyMoves++; }
                game.play(id);
                plies++;
            }
            String r = game.result;
            double s;
            if (r.equals("white") || r.equals("blackResigned")) s = freshWhite ? 1 : 0;
            else if (r.equals("black") || r.equals("whiteResigned")) s = freshWhite ? 0 : 1;
            else if (r.isEmpty()) s = adjudicate(game, freshWhite);
            else s = .5;
            score += s;
            System.out.printf("game %d new=%s result=%s plies=%d material(new)=%d -> %.1f%n", g + 1,
                    freshWhite ? "white" : "black", r.isEmpty() ? "limit" : r, plies,
                    material(game, freshWhite), s);
        }
        System.out.printf("NEW vs OLD (%s): %.1f / %d   avg ms/move new=%.0f old=%.0f%n", level, score, games,
                freshTime / 1e6 / Math.max(1, freshMoves), legacyTime / 1e6 / Math.max(1, legacyMoves));
    }

    private static double adjudicate(Game game, boolean side) {
        int m = material(game, side);
        return m >= 300 ? 1 : m <= -300 ? 0 : .5;
    }

    static int material(Game game, boolean side) {
        int total = 0;
        for (int s = 0; s < game.boardGeometry().size(); s++) {
            var p = game.pieceAt(s);
            if (p == null) continue;
            int v = switch (p.type()) { case 'P' -> 100; case 'N' -> 320; case 'B' -> 330; case 'R' -> 500; case 'Q' -> 900; default -> 0; };
            total += p.white() == side ? v : -v;
        }
        return total;
    }
}
