package chess.game;

import chess.board.Board;
import chess.cards.Card;
import chess.cards.CardContext;
import chess.pieces.Piece;
import chess.pieces.PieceType;

import java.util.List;

/**
 * Движок расходует карту, наследники Card выбирают цели и применяют свой эффект.
 */
final class CardRules {
    private final Game game;
    private final CardContext context;

    CardRules(Game game) {
        this.game = game;
        this.context = new Context(game);
    }

    void addActions(List<Action> out, Card card) {
        out.addAll(card.actions(context));
    }

    void apply(Action action) {
        Card card = game.cardPiles().spend(game.whiteTurn(), action.cardId());
        card.apply(context, action);
        if (card.resetsHalfMoveClock()) game.setHalfMoves(0);
    }

    /**
     * Адаптер даёт картам только нужные операции и не раскрывает внутреннее устройство Game.
     */
    private record Context(Game game) implements CardContext {
        public Board board() { return game.board(); }

        public boolean blocked(int square) { return game.blocked(square); }

        public boolean hasSquareEffect(int square) { return game.effects().visibleAt(square, game.whiteTurn()); }

        public void placeSquareEffect(int square, SquareEffect effect) { game.effects().place(square, effect); }

        public void expandBoard(int left, int right, int bottom, int top) { game.expandBoard(left, right, bottom, top); }

        public int undoTarget() {
            MoveUndo last = game.lastMove();
            return last != null && last.white() != game.whiteTurn() ? last.to() : -1;
        }

        public void undoLastMove(int freezeUntil) {
            if (undoTarget() < 0) throw new IllegalStateException("Нет хода для отмены");
            game.lastMove().restore(game, freezeUntil);
        }

        public boolean whiteTurn() { return game.whiteTurn(); }

        public int ply() { return game.ply(); }

        public List<Card> hand(boolean white) { return game.cardPiles().hand(white); }

        public List<Card> deck() { return game.cardPiles().deck(); }

        public List<Card> discard() { return game.cardPiles().discard(); }

        public List<Piece> captured() { return game.captured(); }

        public List<Integer> startingSquares(PieceType type) { return game.startingSquares(type, game.whiteTurn()); }

        public void prepareScoutDeck() { game.cardPiles().prepareScout(); }

        public Piece newPiece(PieceType type) { return game.newPiece(type, game.whiteTurn()); }

        public void disableCastle(Piece piece, int square) { game.disableCastle(piece, square); }

        public int handLimit(boolean white) { return game.cardPiles().limit(white); }

        public void setHandLimit(boolean white, int limit) { game.cardPiles().setLimit(white, limit); }
    }
}
