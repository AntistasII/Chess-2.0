package chess.ai;

import chess.pieces.PieceType;
import chess.cards.CardType;
import chess.config.GameProperties.SearchLimits;
import chess.game.Action;
import chess.game.Game;
import chess.game.Game.Transition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Итеративный поиск: alpha-beta с главным вариантом (PVS), таблицей лучших ходов,
 * killer- и history-эвристиками, сокращением поздних тихих ходов, продлением шахов
 * и спокойным поиском взятий на границе глубины.
 */
final class SearchEngine {
    static final double MATE = 1_000_000;
    /** Советнику ChatGPT нужны точные оценки всех близких вариантов (допуск 100). */
    static final double ADVISOR_MARGIN = 120;
    private static final int MAX_PLY = 96;
    private static final int MAX_QUIESCENCE = 8;
    private static final int MAX_CHECK_EXTENSION_PLY = 12;
    private static final int TABLE_LIMIT = 400_000;

    private final SearchLimits settings;
    /** Оценки корневых ходов в пределах этого отставания от лучшего остаются точными. */
    private final double rootMargin;
    private long started, deadline;
    private int nodes, qnodes;
    private boolean side;
    private final String[][] killers = new String[MAX_PLY + MAX_QUIESCENCE + 2][2];
    private final Map<String, Integer> history = new HashMap<>();
    /** Только для порядка ходов: неполный ключ позиции не влияет на сами оценки. */
    private final Map<Long, String> bestMoves = new HashMap<>();
    private String partialBest;
    private double partialBestScore, partialFirstScore;

    SearchEngine(SearchLimits settings) { this(settings, ADVISOR_MARGIN); }

    SearchEngine(SearchLimits settings, double rootMargin) {
        this.settings = settings;
        this.rootMargin = Math.max(1, rootMargin);
    }

    int nodesUsed() { return nodes; }

    SearchResult analyze(Game game, Set<String> allowed) {
        side = game.whiteTurn();
        started = System.nanoTime();
        deadline = started + settings.milliseconds() * 1_000_000;
        List<Transition> root = game.legalTransitions(a -> allowed.contains(a.id()));
        if (root.isEmpty()) throw new IllegalStateException("Нет действий");
        List<Transition> ordered = order(game, root, "", 0);
        SearchResult completed = new SearchResult(ordered.getFirst().action().id(), Map.of(), 0);
        for (int depth = 1; depth <= settings.depth(); depth++) {
            // Новая итерация обычно дороже всех предыдущих: не начинаем её без запаса времени.
            if (depth > 2 && (elapsedMillis() > settings.milliseconds() * .45 || nodes > settings.nodes() * .45)) break;
            partialBest = null;
            try {
                completed = searchRoot(game, ordered, depth);
            } catch (BudgetExceeded ignored) {
                // Оценки прерванной итерации не смешиваются с завершённой; берём лишь ход,
                // который уже опроверг прежний лучший вариант на большей глубине.
                // Советнику ChatGPT нужны сопоставимые оценки одной глубины, поэтому только для своего хода.
                if (rootMargin < ADVISOR_MARGIN && partialBest != null && partialBestScore > partialFirstScore
                        && completed.scores().containsKey(partialBest)) {
                    var scores = new LinkedHashMap<>(completed.scores());
                    scores.put(partialBest, partialBestScore);
                    completed = new SearchResult(partialBest, scores, completed.depth());
                }
                break;
            }
            var scores = completed.scores();
            ordered = new ArrayList<>(ordered);
            ordered.sort(Comparator.comparingDouble((Transition t) -> -scores.getOrDefault(t.action().id(), -MATE * 2)));
            double best = scores.get(completed.actionId());
            if (Math.abs(best) > MATE / 2) break;
        }
        if (Boolean.getBoolean("chess.ai.debug"))
            System.out.printf("[ai] q=" + qnodes + " depth=%d nodes=%d ms=%d best=%s %.0f%n", completed.depth(), nodes,
                    elapsedMillis(), completed.actionId(), completed.scores().getOrDefault(completed.actionId(), 0.0));
        return completed;
    }

    long elapsedMillis() { return (System.nanoTime() - started) / 1_000_000; }

    private SearchResult searchRoot(Game game, List<Transition> ordered, int depth) {
        double best = -Double.MAX_VALUE;
        String bestId = ordered.getFirst().action().id();
        var scores = new LinkedHashMap<String, Double>();
        boolean first = true;
        for (Transition t : ordered) {
            checkBudget();
            Action action = t.action();
            Game next = t.next();
            double penalty = MovePreferences.repeatedActionPenalty(game, action)
                    + MovePreferences.reversalPenalty(game, action, next)
                    + MovePreferences.castlingLossPenalty(game, action, next)
                    + (action.isDraw() && EndgameConversion.active(game, side) ? 90 : 0);
            double score;
            if (first) score = search(next, depth - 1, -Double.MAX_VALUE, Double.MAX_VALUE, 1) - penalty;
            else {
                // Окно ниже лучшего на rootMargin: близкие варианты получают точную оценку
                // (её использует советник ChatGPT), далёкие — только верхнюю границу.
                double floor = best - rootMargin + penalty;
                score = search(next, depth - 1, floor, Double.MAX_VALUE, 1) - penalty;
            }
            scores.put(action.id(), score);
            if (first) partialFirstScore = score;
            if (score > best) {
                best = score;
                bestId = action.id();
                partialBest = bestId;
                partialBestScore = score;
            }
            first = false;
        }
        return new SearchResult(bestId, scores, depth);
    }

    private double search(Game game, int depth, double alpha, double beta, int ply) {
        checkBudget();
        boolean inCheck = game.inCheck(game.whiteTurn());
        if (inCheck && ply <= MAX_CHECK_EXTENSION_PLY) depth++;
        if (depth <= 0 || ply >= MAX_PLY) return quiesce(game, alpha, beta, ply, 0);
        nodes++;
        boolean maximizing = game.whiteTurn() == side;
        List<Transition> moves = game.legalTransitions();
        if (moves.isEmpty()) return inCheck ? (maximizing ? -MATE + ply : MATE - ply) : 0;
        long key = PositionHash.of(game);
        List<Transition> ordered = order(game, moves, bestMoves.getOrDefault(key, ""), ply);
        double best = maximizing ? -Double.MAX_VALUE : Double.MAX_VALUE;
        String bestId = null;
        int index = 0;
        for (Transition t : ordered) {
            Action action = t.action();
            Game next = t.next();
            boolean quiet = !tactical(game, action);
            // У границы поиска позиционные карты (поля, заморозка, щит…) почти не меняют
            // исход, но умножают ветвление. Под шахом рассматриваем все спасения.
            if (quiet && index > 0 && !inCheck && depth <= 2 && action.isCard()
                    && !next.inCheck(next.whiteTurn())) continue;
            int newDepth = depth - 1;
            double score;
            if (index == 0) score = search(next, newDepth, alpha, beta, ply + 1);
            else {
                int reduction = 0;
                if (quiet && depth >= 3 && index >= 3 && !inCheck && !next.inCheck(next.whiteTurn()))
                    reduction = index >= 8 && depth >= 5 ? 2 : 1;
                if (maximizing) {
                    score = search(next, newDepth - reduction, alpha, alpha + 1, ply + 1);
                    if (score > alpha && (reduction > 0 || score < beta))
                        score = search(next, newDepth, alpha, beta, ply + 1);
                } else {
                    score = search(next, newDepth - reduction, beta - 1, beta, ply + 1);
                    if (score < beta && (reduction > 0 || score > alpha))
                        score = search(next, newDepth, alpha, beta, ply + 1);
                }
            }
            if (maximizing) {
                if (score > best) { best = score; bestId = action.id(); }
                alpha = Math.max(alpha, best);
            } else {
                if (score < best) { best = score; bestId = action.id(); }
                beta = Math.min(beta, best);
            }
            if (alpha >= beta) {
                if (quiet) {
                    String[] slot = killers[ply];
                    if (!action.id().equals(slot[0])) { slot[1] = slot[0]; slot[0] = action.id(); }
                    history.merge(action.id(), depth * depth, Integer::sum);
                }
                break;
            }
            index++;
        }
        if (bestMoves.size() > TABLE_LIMIT) bestMoves.clear();
        if (bestId != null) bestMoves.put(key, bestId);
        return best;
    }

    /** Спокойный поиск: взятия, превращения и карты возврата материала; под шахом — все спасения. */
    private double quiesce(Game game, double alpha, double beta, int ply, int depth) {
        checkBudget();
        nodes++;
        qnodes++;
        boolean maximizing = game.whiteTurn() == side;
        boolean inCheck = game.inCheck(game.whiteTurn());
        if (inCheck) {
            List<Transition> moves = game.legalTransitions();
            if (moves.isEmpty()) return maximizing ? -MATE + ply : MATE - ply;
            if (depth >= MAX_QUIESCENCE) return PositionEvaluator.evaluate(game, side) * Math.pow(.99, ply);
            double best = maximizing ? -Double.MAX_VALUE : Double.MAX_VALUE;
            for (Transition t : order(game, moves, "", ply)) {
                double score = quiesce(t.next(), alpha, beta, ply + 1, depth + 1);
                if (maximizing) { best = Math.max(best, score); alpha = Math.max(alpha, best); }
                else { best = Math.min(best, score); beta = Math.min(beta, best); }
                if (alpha >= beta) break;
            }
            return best;
        }
        // Ближняя выгода надёжнее отложенной: не откладываем полезную карту без причины.
        double stand = PositionEvaluator.evaluate(game, side) * Math.pow(.99, ply);
        if (depth >= MAX_QUIESCENCE) return stand;
        if (maximizing) {
            if (stand >= beta) return stand;
            alpha = Math.max(alpha, stand);
        } else {
            if (stand <= alpha) return stand;
            beta = Math.min(beta, stand);
        }
        double best = stand;
        var moves = game.legalTransitions(a -> tactical(game, a));
        for (Transition t : order(game, moves, "", ply)) {
            double gain = gain(game, t.action());
            // Даже выигрыш этой фигуры не изменит результат: ход не рассматриваем.
            if (maximizing ? stand + gain + 150 <= alpha : stand - gain - 150 >= beta) continue;
            double score = quiesce(t.next(), alpha, beta, ply + 1, depth + 1);
            if (maximizing) { best = Math.max(best, score); alpha = Math.max(alpha, best); }
            else { best = Math.min(best, score); beta = Math.min(beta, best); }
            if (alpha >= beta) break;
        }
        return best;
    }

    /** Действия, способные сразу изменить материал. */
    static boolean tactical(Game game, Action action) {
        if (action.isMove())
            return game.pieceAt(action.to()) != null || promotion(action) || enPassant(game, action);
        if (!action.isCard()) return false;
        CardType type = cardType(game, action);
        return type == CardType.CONVERT || type == CardType.REVIVE_PAWN || type == CardType.REVIVE_PIECE
                || type == CardType.UNDO_MOVE;
    }

    private static double gain(Game game, Action action) {
        if (action.isCard()) return 900;
        var victim = game.pieceAt(action.to());
        double value = victim == null ? 100 : PositionEvaluator.materialValue(victim.type());
        if (promotion(action)) value += 800;
        return value;
    }

    private List<Transition> order(Game game, List<Transition> moves, String preferred, int ply) {
        String[] killer = ply < killers.length ? killers[ply] : new String[2];
        var keyed = new ArrayList<Map.Entry<Long, Transition>>(moves.size());
        for (Transition t : moves) keyed.add(Map.entry(priority(game, t.action(), preferred, killer), t));
        keyed.sort((a, b) -> {
            int c = Long.compare(b.getKey(), a.getKey());
            return c != 0 ? c : a.getValue().action().id().compareTo(b.getValue().action().id());
        });
        var out = new ArrayList<Transition>(keyed.size());
        for (var e : keyed) out.add(e.getValue());
        return out;
    }

    private long priority(Game game, Action action, String preferred, String[] killer) {
        String id = action.id();
        if (id.equals(preferred)) return 1_000_000_000L;
        if (action.isDraw()) return -1_000_000L + history.getOrDefault(id, 0);
        if (action.isMove()) {
            long value = 0;
            var victim = game.pieceAt(action.to());
            if (victim != null) value += 100_000_000L + PositionEvaluator.materialValue(victim.type()) * 100L
                    - PositionEvaluator.materialValue(game.pieceAt(action.from()).type()) / 10;
            if (promotion(action)) value += 100_000_000L + ("Q".equals(action.option()) ? 90_000 : 0);
            if (enPassant(game, action)) value += 100_010_000L;
            if (value > 0) return value;
        } else if (tactical(game, action)) return 90_000_000L;
        if (id.equals(killer[0])) return 80_000_000L;
        if (id.equals(killer[1])) return 79_000_000L;
        // Тихие ходы и карты без прямой выгоды упорядочивает история отсечений.
        return history.getOrDefault(id, 0) + (action.isCard() ? -200 : 0);
    }

    private static CardType cardType(Game game, Action action) {
        for (var card : game.cards(game.whiteTurn())) if (card.id() == action.cardId()) return card.type();
        return CardType.UNKNOWN;
    }

    private static boolean promotion(Action action) {
        return !action.option().isEmpty() && "QRBN".contains(action.option());
    }

    private static boolean enPassant(Game game, Action action) {
        var piece = game.pieceAt(action.from());
        return piece != null && piece.type() == PieceType.PAWN && game.pieceAt(action.to()) == null
                && game.boardGeometry().file(action.from()) != game.boardGeometry().file(action.to());
    }

    private void checkBudget() {
        if (nodes >= settings.nodes() || System.nanoTime() >= deadline || Thread.currentThread().isInterrupted())
            throw new BudgetExceeded();
    }

    private static final class BudgetExceeded extends RuntimeException {
        BudgetExceeded() { super(null, null, false, false); }
    }
}
