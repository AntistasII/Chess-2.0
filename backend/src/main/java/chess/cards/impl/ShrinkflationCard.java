package chess.cards.impl;

import chess.cards.Card;
import chess.cards.CardContext;
import chess.cards.CardDefinition;
import chess.cards.CardType;
import chess.game.Action;
import chess.game.GameSettings;

import java.util.List;

/**
 * Вместимость руки соперника уменьшается на одну карту (не меньше 1). Его карты не
 * сбрасываются: при переполненной руке добор недоступен, пока он не потратит лишние.
 */
@CardDefinition(type = CardType.SHRINKFLATION, copies = 2)
public final class ShrinkflationCard extends Card {
    public ShrinkflationCard(int id) {
        super(id, CardType.SHRINKFLATION, "Шринкфляция");
    }

    @Override
    public boolean resetsHalfMoveClock() {
        return false;
    }

    @Override
    public List<Action> actions(CardContext context) {
        return context.handLimit(!context.whiteTurn()) > GameSettings.MIN_HAND_LIMIT
                ? List.of(Action.card(this, -1, -1, "")) : List.of();
    }

    @Override
    public void apply(CardContext context, Action action) {
        boolean enemy = !context.whiteTurn();
        context.setHandLimit(enemy, context.handLimit(enemy) - 1);
    }
}
