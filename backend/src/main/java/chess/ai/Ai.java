package chess.ai;

import chess.game.Game;
import chess.game.MoveSelector;
import chess.config.GameProperties;
import java.util.Objects;
import java.util.Set;

/** Точка входа локального ИИ. Каждый поиск работает только с доступной игроку информацией. */
public final class Ai implements MoveSelector {
    private final GameProperties properties;

    public Ai(GameProperties properties) { this.properties = Objects.requireNonNull(properties); }

    @Override public String choose(Game game) {
        Game observation = game.observationForCurrentPlayer();
        var hints = TacticalHints.forPosition(observation);
        var allowed = TacticalHints.saferActionIds(observation, hints);
        if (CardDrawPolicy.shouldDraw(observation, hints, allowed)) return "draw";
        return analyze(observation, allowed).actionId();
    }
    /** Та же проверка поиска используется как советник ChatGPT без подмены его ответа. */
    public SearchResult analyze(Game game, Set<String> allowed) {
        Game position = game.observationForCurrentPlayer();
        var limits = properties.searchFor(game.difficulty());
        if (EndgameConversion.active(position, position.whiteTurn())) {
            var matingSearch = new MatingSearch(limits);
            var mate = matingSearch.find(position, allowed);
            if (mate != null) return new SearchResult(mate.actionId(), java.util.Map.of(), 0, mate.plies());
            limits = new GameProperties.SearchLimits(limits.depth(), Math.max(1, limits.nodes()-matingSearch.nodesUsed()),
                    Math.max(1, limits.milliseconds()-matingSearch.elapsedMillis()));
        }
        return new SearchEngine(limits).analyze(position, allowed);
    }
}
