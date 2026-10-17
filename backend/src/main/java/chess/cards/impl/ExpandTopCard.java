package chess.cards.impl;

import chess.cards.*;

@CardDefinition(type = CardType.EXPAND_TOP, copies = 2)
public final class ExpandTopCard extends ExpansionCard {
    public ExpandTopCard(int id) { super(id, CardType.EXPAND_TOP, "Увеличить сверху", 0, 0, 0, 1); }
}
