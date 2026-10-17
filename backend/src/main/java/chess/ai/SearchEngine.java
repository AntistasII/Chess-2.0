package chess.ai;

import chess.game.Action;
import chess.game.Game;
import chess.config.GameProperties.SearchLimits;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashMap;

/** Итеративный minimax с alpha-beta и продолжением тактических разменов на границе поиска. */
final class SearchEngine {
    private static final double MATE = 1_000_000;
    private final SearchLimits settings;
    private long deadline;
    private int nodes;
    private boolean side;

    SearchEngine(SearchLimits settings) { this.settings = settings; }

    SearchResult analyze(Game game, Set<String> allowed) {
        side = game.whiteTurn();
        deadline = System.nanoTime() + settings.milliseconds() * 1_000_000;
        List<Action> legal = game.legalActions().stream().filter(a -> allowed.contains(a.id())).toList();
        if (legal.isEmpty()) throw new IllegalStateException("Нет действий");
        String preferred = ActionOrdering.ordered(game, legal, "").getFirst().id();
        SearchResult completed = new SearchResult(preferred, java.util.Map.of(), 0);
        for (int depth = 1; depth <= settings.depth(); depth++) {
            try {
                completed = searchRoot(game, legal, completed.actionId(), depth);
            } catch (BudgetExceeded ignored) {
                // Частичные оценки не смешиваются с результатами завершённой глубины.
                break;
            }
        }
        return completed;
    }

    private SearchResult searchRoot(Game game, List<Action> legal, String preferred, int depth) {
        double bestScore = -Double.MAX_VALUE;
        String best = preferred;
        var scores = new LinkedHashMap<String, Double>();
        for (Action action : ActionOrdering.ordered(game, legal, preferred)) {
            checkBudget();
            // Каждый корневой кандидат получает собственную оценку, а не границу alpha.
            Game next = game.simulate(action);
            double score = search(next, depth - 1, depth == 1 ? 0 : 2,
                    -Double.MAX_VALUE, Double.MAX_VALUE, 1)
                    - MovePreferences.reversalPenalty(game, action, next)
                    - MovePreferences.castlingLossPenalty(game, action, next)
                    - (action.kind().equals("draw") && EndgameConversion.active(game, side) ? 90 : 0);
            scores.put(action.id(), score);
            if (score > bestScore) { bestScore = score; best = action.id(); }
        }
        return new SearchResult(best, scores, depth);
    }

    private double search(Game game, int depth, int tacticalDepth, double alpha, double beta, int ply) {
        checkBudget();
        nodes++;
        boolean maximizing = game.whiteTurn() == side;
        boolean inCheck = game.inCheck(game.whiteTurn());
        List<Action> legal = game.legalActions();
        // Проверяем мат и пат также в листьях; карточное спасение входит в legalActions.
        if (legal.isEmpty()) return inCheck ? (maximizing ? -MATE + ply : MATE - ply) : 0;
        double best = maximizing ? -Double.MAX_VALUE : Double.MAX_VALUE;
        if (depth <= 0) {
            // Близкая выгода надёжнее отложенной: не откладываем полезную карту без причины.
            best = PositionEvaluator.evaluate(game, side) * Math.pow(.99, ply);
            if (tacticalDepth == 0) return best;
            // Под шахом нельзя «остаться на месте»: проверяем все спасения, включая карты.
            if (!inCheck) {
                if (maximizing) alpha = Math.max(alpha, best);
                else beta = Math.min(beta, best);
                if (alpha >= beta) return best;
                legal = legal.stream().filter(a -> ActionOrdering.forcing(game, a)).toList();
            } else best = maximizing ? -Double.MAX_VALUE : Double.MAX_VALUE;
        }
        for (Action action : ActionOrdering.ordered(game, legal, "")) {
            double score = search(game.simulate(action), depth - 1,
                    depth <= 0 ? tacticalDepth - 1 : tacticalDepth, alpha, beta, ply + 1);
            if (maximizing) { best = Math.max(best, score); alpha = Math.max(alpha, best); }
            else { best = Math.min(best, score); beta = Math.min(beta, best); }
            if (alpha >= beta) break;
        }
        return best;
    }

    private void checkBudget() {
        if (nodes >= settings.nodes() || System.nanoTime() >= deadline || Thread.currentThread().isInterrupted())
            throw new BudgetExceeded();
    }

    private static final class BudgetExceeded extends RuntimeException {
        BudgetExceeded() { super(null, null, false, false); }
    }
}
