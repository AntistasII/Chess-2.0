package chess.cards.impl;

import chess.pieces.PieceType;
import chess.cards.CardType;
import chess.cards.CardDefinition;
import chess.cards.ReviveCard;

@CardDefinition(type = CardType.REVIVE_PIECE, copies = 2)
public final class RevivePieceCard extends ReviveCard {
    public RevivePieceCard(int id) {
        super(id, CardType.REVIVE_PIECE, "Возрождение фигуры");
    }

    @Override
    protected boolean accepts(PieceType type) {
        return type != PieceType.PAWN && type != PieceType.KING;
    }
}
