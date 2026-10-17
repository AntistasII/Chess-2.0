package chess.cards.impl;

import chess.cards.Card;
import chess.cards.CardType;
import chess.cards.CardDefinition;
import chess.cards.CardContext;

import chess.game.Action;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@CardDefinition(type = CardType.EXCHANGE, copies = 2)
public final class ExchangeCard extends Card {
    public ExchangeCard(int id) {
        super(id, CardType.EXCHANGE, "Обмен планами");
    }

    @Override
    public boolean resetsHalfMoveClock() {
        return false;
    }

    @Override
    public List<Action> actions(CardContext context) {
        var actions = new ArrayList<Action>();
        if (!context.hand(!context.whiteTurn()).isEmpty())
            for (Card own : context.hand(context.whiteTurn()))
                if (own.id() != id())
                    actions.add(Action.card(this, -1, own.id(), ""));
        return actions;
    }

    @Override
    public void apply(CardContext context, Action action) {
        var hand = context.hand(context.whiteTurn());
        Card own = hand.stream().filter(c -> c.id() == action.other()).findFirst().orElseThrow();
        var enemy = context.hand(!context.whiteTurn());
        Card theirs = enemy.set(ThreadLocalRandom.current().nextInt(enemy.size()), own);
        hand.remove(own);
        hand.add(theirs);
    }
}
