package chess.cards.impl;

import chess.cards.CardType;
import chess.cards.CardDefinition;
import chess.cards.CardContext;
import chess.cards.TargetedCard;

import chess.game.Action;
import chess.pieces.Piece;

@CardDefinition(type = CardType.SHIELD, copies = 2)
public final class ShieldCard extends TargetedCard {
    public ShieldCard(int id) {
        super(id, CardType.SHIELD, "Щит");
    }

    @Override
    public boolean canTarget(Piece piece, boolean own) {
        return own && !piece.shield();
    }

    @Override
    public void applyTo(CardContext context, Action action) {
        Piece piece = context.board().get(action.to());
        piece.protect();
    }
}
