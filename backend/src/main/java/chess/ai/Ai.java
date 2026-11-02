package chess.ai;

import chess.game.Action;
import chess.game.Difficulty;
import chess.game.Game;
import chess.game.MoveSelector;
import chess.config.GameProperties;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** Точка входа локального ИИ. Каждый поиск работает только с доступной игроку информацией. */
public final class Ai implements MoveSelector {
    private final GameProperties properties;
    private final Random random;

    public Ai(GameProperties properties) { this(properties, new Random()); }

    Ai(GameProperties properties, Random random) {
        this.properties = Objects.requireNonNull(properties);
        this.random = Objects.requireNonNull(random);
    }

    @Override public String choose(Game game) {
        Game observation = game.observationForCurrentPlayer();
        var hints = TacticalHints.forPosition(observation);
        var allowed = TacticalHints.saferActionIds(observation, hints);
        if (CardDrawPolicy.shouldDraw(observation, hints, allowed)) return Action.DRAW_ID;
        double variety = variety(game.difficulty());
        var result = analyze(observation, allowed, variety);
        return varied(result, variety);
    }

    /** Та же проверка поиска используется как советник ChatGPT без подмены его ответа. */
    public SearchResult analyze(Game game, Set<String> allowed) {
        return analyze(game, allowed, SearchEngine.ADVISOR_MARGIN);
    }

    private SearchResult analyze(Game game, Set<String> allowed, double rootMargin) {
        Game position = game.observationForCurrentPlayer();
        var limits = properties.searchFor(game.difficulty().id());
        if (EndgameConversion.active(position, position.whiteTurn())) {
            var matingSearch = new MatingSearch(limits);
            var mate = matingSearch.find(position, allowed);
            if (mate != null) return new SearchResult(mate.actionId(), java.util.Map.of(), 0, mate.plies());
            limits = new GameProperties.SearchLimits(limits.depth(), Math.max(1, limits.nodes()-matingSearch.nodesUsed()),
                    Math.max(1, limits.milliseconds()-matingSearch.elapsedMillis()));
        }
        return new SearchEngine(limits, rootMargin).analyze(position, allowed);
    }

    /**
     * Разнообразие партий: среди почти равных по оценке ходов выбираем случайно,
     * лучший — вероятнее. Допуск мал, поэтому ход не становится заметно слабее.
     */
    private String varied(SearchResult result, double variety) {
        if (result.matePlies() > 0 || result.depth() < 2 || result.scores().size() < 2) return result.actionId();
        Double bestValue = result.scores().get(result.actionId());
        if (bestValue == null || Math.abs(bestValue) > SearchEngine.MATE / 2) return result.actionId();
        double best = bestValue;
        // Разнообразие только среди точных оценок завершённой глубины: если лучший ход
        // взят из прерванной итерации, оценки прочих ходов с ним несравнимы.
        for (double v : result.scores().values()) if (v > best) return result.actionId();
        var ids = new ArrayList<String>();
        var weights = new ArrayList<Double>();
        double total = 0;
        for (var e : result.scores().entrySet()) {
            double gap = best - e.getValue();
            if (gap < 0 || gap >= variety) continue;
            double weight = Math.exp(-3 * gap / Math.max(1, variety));
            ids.add(e.getKey());
            weights.add(weight);
            total += weight;
        }
        if (Boolean.getBoolean("chess.ai.debug")) System.out.println("[ai] variety pool " + ids + " from best " + result.actionId() + " " + best + " scores " + result.scores());
        if (ids.size() < 2) return result.actionId();
        double pick = random.nextDouble() * total;
        for (int i = 0; i < ids.size(); i++) {
            pick -= weights.get(i);
            if (pick <= 0) return ids.get(i);
        }
        return result.actionId();
    }

    /** Допуск разнообразия в сотых пешки: на лёгком уровне ИИ чаще выбирает «человеческие» ходы. */
    static double variety(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 45;
            case NORMAL -> 14;
            case HARD -> 6;
        };
    }
}
