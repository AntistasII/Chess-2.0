package chess.cards.impl;

import chess.cards.*;

@CardDefinition(type = CardType.EXPAND_RIGHT, copies = 2)
public final class ExpandRightCard extends ExpansionCard {
    public ExpandRightCard(int id) { super(id, CardType.EXPAND_RIGHT, "Увеличить справа", 0, 1, 0, 0); }
}
