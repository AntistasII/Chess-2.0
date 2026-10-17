package chess.cards.impl;

import chess.cards.Card;
import chess.cards.CardType;
import chess.cards.CardDefinition;
import chess.cards.CardContext;

import chess.game.Action;

import java.util.List;

/**
 * Заглушка скрытой карты для расчётов ИИ: не имеет доступных действий.
 */
@CardDefinition(type = CardType.UNKNOWN, copies = 0)
public final class UnknownCard extends Card {
    public UnknownCard(int id) {
        super(id, CardType.UNKNOWN, "Скрытая карта");
    }

    @Override
    public List<Action> actions(CardContext context) {
        return List.of();
    }

    @Override
    public void apply(CardContext context, Action action) {
        throw new IllegalArgumentException("Нельзя применить скрытую карту");
    }
}
