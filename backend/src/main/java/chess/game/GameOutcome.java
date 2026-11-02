package chess.game;

import chess.cards.Card;
import chess.cards.CardType;
import chess.pieces.Piece;

import java.util.List;
import java.util.Map;

/** Мат, пат, автоматические ничьи, право заявить ничью и ключ повторения позиции. */
final class GameOutcome {
    /** Действий без хода пешкой и взятия до права заявить ничью и до автоматической ничьей. */
    static final int CLAIM_HALF_MOVES = 100, AUTOMATIC_HALF_MOVES = 150;
    /** Повторений позиции до права заявить ничью и до автоматической ничьей. */
    static final int CLAIM_REPETITIONS = 3, AUTOMATIC_REPETITIONS = 5;

    private final Game game;

    GameOutcome(Game game) { this.game = game; }

    void refreshResult() {
        if (game.legalActions().isEmpty()) {
            boolean white = game.whiteTurn();
            game.setResult(game.inCheck(white) ? GameResult.checkmate(!white) : GameResult.STALEMATE);
            return;
        }
        // Мат имеет приоритет перед автоматическими ничьими.
        if (game.halfMoves() >= AUTOMATIC_HALF_MOVES) game.setResult(GameResult.SEVENTY_FIVE_MOVES);
        else if (repetitions() >= AUTOMATIC_REPETITIONS) game.setResult(GameResult.REPETITION);
    }

    boolean canClaimDraw() {
        return !game.scoutingActive() && !game.result().finished()
                && (game.halfMoves() >= CLAIM_HALF_MOVES || repetitions() >= CLAIM_REPETITIONS);
    }

    private int repetitions() { return game.repetitions().getOrDefault(positionKey(), 0); }

    /**
     * Ключ записывается в сохранения: формат строки должен оставаться прежним,
     * иначе загруженные партии потеряют счёт повторений.
     */
    String positionKey() {
        int ply = game.ply();
        var s = new StringBuilder().append(game.boardGeometry())
                .append(':').append(game.whiteTurn()).append(':').append(game.enPassant())
                .append(':').append(game.castling());
        for (Piece p : game.board()) {
            s.append('/');
            if (p != null)
                s.append(p.type().code()).append(p.white()).append(p.moved()).append(p.boost()).append(p.shield())
                        .append(Math.max(0, p.frozenUntil() - ply))
                        .append(':').append(Math.max(0, p.astralUntil() - ply));
        }
        var piles = game.cardPiles();
        for (var list : List.of(piles.hand(true), piles.hand(false), piles.deck(), piles.discard())) {
            s.append('|');
            for (Card c : list) s.append(c.type()).append(',');
        }
        s.append('|');
        game.captured().stream().map(p -> "" + p.type().code() + p.white()).sorted().forEach(s::append);
        if (!game.effects().isEmpty()) {
            s.append("|fields:");
            for (Map.Entry<Integer, SquareEffect> e : game.effects().sorted().entrySet())
                s.append(e.getKey()).append(':').append(e.getValue().type()).append(':').append(e.getValue().white())
                        .append(':').append(e.getValue().expiresAt() - ply).append(':').append(e.getValue().freezeTurns()).append(';');
        }
        // Добавляется только при изменённых лимитах, чтобы ключи старых сохранений не менялись.
        if (game.handLimitsChanged())
            s.append("|limits:").append(game.handLimit(true)).append(',').append(game.handLimit(false));
        if (game.lastMove() != null && game.holds(game.whiteTurn(), CardType.UNDO_MOVE))
            s.append("|undo:").append(game.lastMove().positionKey(ply));
        return s.toString();
    }
}
