package chess.cards.impl;

import chess.cards.CardType;
import chess.cards.CardDefinition;
import chess.cards.CardContext;
import chess.cards.TargetedCard;

import chess.game.Action;
import chess.pieces.Piece;

@CardDefinition(type = CardType.CONVERT, copies = 2)
public final class ConvertCard extends TargetedCard {
    public ConvertCard(int id) {
        super(id, CardType.CONVERT, "Смена стороны");
    }

    @Override
    public boolean canTarget(Piece piece, boolean own) {
        return !own && piece.type() == 'P';
    }

    @Override
    public void applyTo(CardContext context, Action action) {
        Piece piece = context.board().get(action.to());
        piece.changeSide(context.whiteTurn());
        piece.markMoved();
    }
}
