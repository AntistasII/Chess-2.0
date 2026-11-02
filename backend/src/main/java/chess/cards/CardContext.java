package chess.cards;

import chess.board.Board;
import chess.pieces.Piece;
import chess.pieces.PieceType;

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

    List<Integer> startingSquares(PieceType type);

    void prepareScoutDeck();

    Piece newPiece(PieceType type);

    void disableCastle(Piece piece, int square);

    /** Вместимость руки игрока. */
    int handLimit(boolean white);

    /** Меняет вместимость руки; допустимы значения от 1 до 12. */
    void setHandLimit(boolean white, int limit);
}
