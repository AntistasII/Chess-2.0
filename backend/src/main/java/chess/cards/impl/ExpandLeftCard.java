package chess.cards.impl;

import chess.cards.*;

@CardDefinition(type = CardType.EXPAND_LEFT, copies = 2)
public final class ExpandLeftCard extends ExpansionCard {
    public ExpandLeftCard(int id) { super(id, CardType.EXPAND_LEFT, "Увеличить слева", 1, 0, 0, 0); }
}
