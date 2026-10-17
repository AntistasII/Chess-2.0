package chess.game;

import chess.board.Board;
import chess.cards.Card;
import chess.cards.CardContext;
import chess.pieces.Piece;
import chess.pieces.PieceFactory;

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
        var hand = game.hand(game.whiteTurn);
        Card card = hand.stream().filter(c -> c.id() == action.cardId()).findFirst().orElseThrow();
        hand.remove(card);
        game.discard.add(card);
        card.apply(context, action);
        if (card.resetsHalfMoveClock()) game.halfMoves = 0;
    }

    /**
     * Адаптер не раскрывает внутренние поля Game за пределами игрового пакета.
     */
    private record Context(Game game) implements CardContext {
        public boolean blocked(int square) { return game.blocked(square); }
        public boolean hasSquareEffect(int square) { return game.squareEffects.containsKey(square) && game.squareEffects.get(square).visibleTo(game.whiteTurn); }
        public void placeSquareEffect(int square, SquareEffect effect) { game.squareEffects.put(square, effect); }
        public void expandBoard(int left, int right, int bottom, int top) { game.expandBoard(left, right, bottom, top); }
        public int undoTarget() { return game.lastMove != null && game.lastMove.white() != game.whiteTurn ? game.lastMove.to() : -1; }
        public void undoLastMove(int freezeUntil) {
            if (undoTarget() < 0) throw new IllegalStateException("Нет хода для отмены");
            game.lastMove.restore(game, freezeUntil);
        }

        public Board board() {
            return game.board;
        }

        public boolean whiteTurn() {
            return game.whiteTurn;
        }

        public int ply() {
            return game.ply;
        }

        public List<Card> hand(boolean white) {
            return game.hand(white);
        }

        public List<Card> deck() {
            return game.deck;
        }

        public List<Card> discard() {
            return game.discard;
        }

        public List<Piece> captured() {
            return game.captured;
        }

        public List<Integer> startingSquares(char type) {
            return game.startingSquares(type, game.whiteTurn);
        }

        public void prepareScoutDeck() {
            game.ensureScoutDeck();
        }

        public Piece newPiece(char type) {
            return PieceFactory.create(game.nextPieceId++, type, game.whiteTurn);
        }

        public void disableCastle(Piece piece, int square) {
            game.disableCastle(piece, square);
        }
    }
}
