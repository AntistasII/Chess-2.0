package chess.ai;

import chess.config.GameProperties.SearchLimits;
import chess.game.Game;
import java.util.Set;

/** Ограниченный поиск мата за два своих хода через шахи. Скрытые карты остаются неизвестными. */
final class MatingSearch {
    record Plan(String actionId, int plies) {}
    private final long deadline;
    private final int limit;
    private int nodes;

    MatingSearch(SearchLimits settings) {
        deadline = System.nanoTime() + Math.min(300,Math.max(1,settings.milliseconds()/4))*1_000_000;
        limit = Math.max(1,Math.min(600,settings.nodes()/3));
    }

    Plan find(Game game, Set<String> allowed) {
        boolean side = game.whiteTurn();
        try {
            for (var move : game.legalActions()) {
                if (!allowed.contains(move.id())) continue;
                tick();
                Game next = game.simulate(move);
                if (!next.inCheck(!side)) continue;
                var replies = next.legalActions();
                if (replies.isEmpty()) return new Plan(move.id(),1);
                boolean forced = true;
                for (var reply : replies) {
                    tick();
                    Game afterReply = next.simulate(reply);
                    boolean finish = false;
                    for (var attack : afterReply.legalActions()) {
                        tick();
                        Game end = afterReply.simulate(attack);
                        if (end.inCheck(!side) && end.legalActions().isEmpty()) { finish = true; break; }
                    }
                    if (!finish) { forced = false; break; }
                }
                if (forced) return new Plan(move.id(),3);
            }
        } catch (LimitReached ignored) { /* Частичная линия не считается найденным матом. */ }
        return null;
    }

    private void tick() {
        if (++nodes > limit || System.nanoTime() >= deadline || Thread.currentThread().isInterrupted()) throw new LimitReached();
    }
    private static final class LimitReached extends RuntimeException {
        LimitReached() { super(null,null,false,false); }
    }
}
