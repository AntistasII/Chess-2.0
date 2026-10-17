package chess.cards;

import chess.board.Board;
import chess.pieces.Piece;

import java.util.List;

/**
 * Доступ карты к игровой позиции. Очередь, законность и расход карты остаются у движка.
 */
public interface CardContext {
    Board board();

    boolean blocked(int square);

    boolean hasSquareEffect(int square);

    void placeSquareEffect(int square, chess.game.SquareEffect effect);

    void expandBoard(int left, int right, int bottom, int top);

    int undoTarget();

    void undoLastMove(int freezeUntil);

    boolean whiteTurn();

    int ply();

    List<Card> hand(boolean white);

    List<Card> deck();

    List<Card> discard();

    List<Piece> captured();

    List<Integer> startingSquares(char type);

    void prepareScoutDeck();

    Piece newPiece(char type);

    void disableCastle(Piece piece, int square);
}
