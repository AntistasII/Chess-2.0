package chess.cards.impl;

import chess.cards.CardType;
import chess.cards.CardDefinition;
import chess.cards.CardContext;
import chess.cards.TargetedCard;

import chess.game.Action;
import chess.pieces.Piece;

@CardDefinition(type = CardType.KING_BOOST, copies = 2)
public final class KingBoostCard extends TargetedCard {
    public KingBoostCard(int id) {
        super(id, CardType.KING_BOOST, "Усиление короля");
    }

    @Override
    public boolean canTarget(Piece piece, boolean own) {
        return own && piece.type() == 'K' && !piece.boost();
    }

    @Override
    public void applyTo(CardContext context, Action action) {
        Piece piece = context.board().get(action.to());
        piece.enableBoost();
    }
}
