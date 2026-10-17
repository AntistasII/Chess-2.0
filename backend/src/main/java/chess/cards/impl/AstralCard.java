package chess.cards.impl;

import chess.cards.CardType;
import chess.cards.CardDefinition;
import chess.cards.CardContext;
import chess.cards.TargetedCard;

import chess.game.Action;
import chess.pieces.Piece;

@CardDefinition(type = CardType.ASTRAL, copies = 2, durationTurns = 2)
public final class AstralCard extends TargetedCard {
    public AstralCard(int id) {
        super(id, CardType.ASTRAL, "Астрал");
    }

    @Override
    public boolean canTarget(Piece piece, boolean own) {
        return !own && piece.type() != 'K';
    }

    @Override
    public void applyTo(CardContext context, Action action) {
        Piece piece = context.board().get(action.to());
        piece.sendToAstralUntil(effectEndsAt(context.ply()));
    }
}
