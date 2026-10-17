package chess.cards;

import chess.game.Action;
import chess.game.SquareEffect;
import java.util.ArrayList;
import java.util.List;

/** Заклинание на пустую клетку без другого активного поля. */
public abstract class SquareEffectCard extends Card {
    private final SquareEffect.Type effect;
    protected SquareEffectCard(int id, CardType type, String name, SquareEffect.Type effect) {
        super(id, type, name);
        this.effect = effect;
    }
    @Override public List<Action> actions(CardContext context) {
        var actions = new ArrayList<Action>();
        for (int square = 0; square < context.board().size(); square++)
            if (context.board().get(square) == null && !context.hasSquareEffect(square))
                actions.add(Action.card(this, square, -1, ""));
        return actions;
    }
    @Override public void apply(CardContext context, Action action) {
        context.placeSquareEffect(action.to(), new SquareEffect(effect, context.whiteTurn(),
                effectEndsAt(context.ply()), freezeTurns()));
    }
}
