package chess.cards.impl;

import chess.cards.CardType;
import chess.cards.CardDefinition;
import chess.cards.CardContext;
import chess.cards.TargetedCard;

import chess.game.Action;
import chess.pieces.Piece;

@CardDefinition(type = CardType.FREEZE, copies = 2, durationTurns = 2)
public final class FreezeCard extends TargetedCard {
    public FreezeCard(int id) {
        super(id, CardType.FREEZE, "Заморозка");
    }

    @Override
    public boolean canTarget(Piece piece, boolean own) {
        return !own && piece.type() != 'K' && !piece.frozen();
    }

    @Override
    public void applyTo(CardContext context, Action action) {
        Piece piece = context.board().get(action.to());
        piece.freezeUntil(effectEndsAt(context.ply()));
    }
}
