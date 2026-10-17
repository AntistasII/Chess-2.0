package chess.cards.impl;

import chess.cards.CardType;
import chess.cards.CardDefinition;
import chess.cards.ReviveCard;

@CardDefinition(type = CardType.REVIVE_PAWN, copies = 2)
public final class RevivePawnCard extends ReviveCard {
    public RevivePawnCard(int id) {
        super(id, CardType.REVIVE_PAWN, "Возрождение пешки");
    }

    @Override
    protected boolean accepts(char type) {
        return type == 'P';
    }
}
