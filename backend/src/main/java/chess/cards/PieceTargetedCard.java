package chess.cards;

import chess.game.Action;
import chess.pieces.Piece;

/**
 * Карта, применяемая к фигуре или паре фигур на доске.
 */
public interface PieceTargetedCard {
    boolean canTarget(Piece piece, boolean own);

    void applyTo(CardContext context, Action action);
}
