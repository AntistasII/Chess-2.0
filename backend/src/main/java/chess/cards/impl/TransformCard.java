package chess.cards.impl;

import chess.cards.CardContext;
import chess.cards.CardDefinition;
import chess.cards.CardType;
import chess.cards.TargetedCard;
import chess.game.Action;
import chess.pieces.Piece;
import chess.pieces.PieceType;

/** Свой слон становится конём, а конь — слоном. Эффекты и статус «ходила» сохраняются. */
@CardDefinition(type = CardType.TRANSFORM, copies = 2)
public final class TransformCard extends TargetedCard {
    public TransformCard(int id) {
        super(id, CardType.TRANSFORM, "Превращение");
    }

    @Override
    public boolean canTarget(Piece piece, boolean own) {
        return own && (piece.type() == PieceType.BISHOP || piece.type() == PieceType.KNIGHT);
    }

    @Override
    public void applyTo(CardContext context, Action action) {
        Piece piece = context.board().get(action.to());
        PieceType other = piece.type() == PieceType.BISHOP ? PieceType.KNIGHT : PieceType.BISHOP;
        context.board().set(action.to(), piece.withType(other));
    }
}
