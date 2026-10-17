package chess.cards.impl;

import chess.cards.CardType;
import chess.cards.CardDefinition;
import chess.cards.CardContext;
import chess.cards.TargetedCard;

import chess.game.Action;
import chess.pieces.Piece;

@CardDefinition(type = CardType.CLEANSE, copies = 2)
public final class CleanseCard extends TargetedCard {
    public CleanseCard(int id) {
        super(id, CardType.CLEANSE, "Очищение");
    }

    @Override
    public boolean canTarget(Piece piece, boolean own) {
        return piece.boost() || piece.shield() || piece.frozen();
    }

    @Override
    public void applyTo(CardContext context, Action action) {
        Piece piece = context.board().get(action.to());
        piece.clearEffects();
    }
}
