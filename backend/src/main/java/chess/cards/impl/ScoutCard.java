package chess.cards.impl;

import chess.cards.Card;
import chess.cards.CardType;
import chess.cards.CardDefinition;
import chess.cards.CardContext;

import chess.game.Action;

import java.util.Collections;
import java.util.List;

@CardDefinition(type = CardType.SCOUT, copies = 2)
public final class ScoutCard extends Card {
    /** Варианты разведки: оставить порядок двух верхних карт или поменять их местами. */
    public static final String KEEP = "keep", REVERSE = "reverse";

    public ScoutCard(int id) {
        super(id, CardType.SCOUT, "Разведка");
    }

    @Override
    public boolean resetsHalfMoveClock() {
        return false;
    }

    @Override
    public List<Action> actions(CardContext context) {
        return context.deck().size() + context.discard().size() < 2 ? List.of()
                : List.of(Action.card(this, -1, -1, KEEP), Action.card(this, -1, -1, REVERSE));
    }

    @Override
    public void apply(CardContext context, Action action) {
        context.prepareScoutDeck();
        if (action.option().equals(REVERSE)) Collections.swap(context.deck(), 0, 1);
    }
}
