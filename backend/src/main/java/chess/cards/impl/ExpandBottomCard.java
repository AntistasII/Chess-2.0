package chess.cards.impl;

import chess.cards.*;

@CardDefinition(type = CardType.EXPAND_BOTTOM, copies = 2)
public final class ExpandBottomCard extends ExpansionCard {
    public ExpandBottomCard(int id) { super(id, CardType.EXPAND_BOTTOM, "Увеличить снизу", 0, 0, 1, 0); }
}
