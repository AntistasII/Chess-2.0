package chess.cards.impl;

import chess.cards.Card;
import chess.cards.CardContext;
import chess.cards.CardDefinition;
import chess.cards.CardType;
import chess.game.Action;
import chess.game.GameSettings;

import java.util.List;

/** Вместимость своей руки растёт на одну карту (не больше 12). */
@CardDefinition(type = CardType.INFLATION, copies = 2)
public final class InflationCard extends Card {
    public InflationCard(int id) {
        super(id, CardType.INFLATION, "Инфляция");
    }

    @Override
    public boolean resetsHalfMoveClock() {
        return false;
    }

    @Override
    public List<Action> actions(CardContext context) {
        return context.handLimit(context.whiteTurn()) < GameSettings.MAX_HAND_LIMIT
                ? List.of(Action.card(this, -1, -1, "")) : List.of();
    }

    @Override
    public void apply(CardContext context, Action action) {
        boolean white = context.whiteTurn();
        context.setHandLimit(white, context.handLimit(white) + 1);
    }
}
