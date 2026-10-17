package chess.game;

import chess.cards.Card;
import chess.pieces.Piece;
import java.util.List;

/** Повторения позиции, мат, пат и условия ничьей. */
final class GameOutcome {
    private final Game game;

    GameOutcome(Game game) { this.game = game; }

    void refreshResult() {
        if (game.legalActions().isEmpty()) {
            game.result = game.inCheck(game.whiteTurn) ? (game.whiteTurn ? "black" : "white") : "stalemate";
            return;
        }
        // Мат имеет приоритет перед автоматическими ничьими.
        if (game.halfMoves >= 150) game.result = "seventyFiveMoves";
        else if (game.repetitions.getOrDefault(positionKey(), 0) >= 5) game.result = "repetition";
    }

    boolean canClaimDraw() {
        return game.scouting < 0 && game.result.isEmpty() && (game.halfMoves >= 100 || game.repetitions.getOrDefault(positionKey(), 0) >= 3);
    }

    String positionKey() {
        // Порядок полей сохраняет совместимость ключей в уже записанных партиях.
        var s = new StringBuilder().append(game.board.geometry())
                .append(':').append(game.whiteTurn).append(':').append(game.enPassant)
                .append(':').append(game.wKingCastle).append(game.wQueenCastle)
                .append(game.bKingCastle).append(game.bQueenCastle);
        for (Piece p : game.board) {
            s.append('/');
            if (p != null)
                s.append(p.type()).append(p.white()).append(p.moved()).append(p.boost()).append(p.shield())
                        .append(Math.max(0, p.frozenUntil() - game.ply))
                        .append(':').append(Math.max(0, p.astralUntil() - game.ply));
        }
        for (var list : List.of(game.whiteHand, game.blackHand, game.deck, game.discard)) {
            s.append('|');
            for (Card c : list) s.append(c.type()).append(',');
        }
        s.append('|');
        game.captured.stream().map(p -> "" + p.type() + p.white()).sorted().forEach(s::append);
        if (!game.squareEffects.isEmpty()) {
            s.append("|fields:");
            game.squareEffects.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(e ->
                    s.append(e.getKey()).append(':').append(e.getValue().type()).append(':').append(e.getValue().white())
                            .append(':').append(e.getValue().expiresAt() - game.ply).append(':').append(e.getValue().freezeTurns()).append(';'));
        }
        if (game.lastMove != null && game.hand(game.whiteTurn).stream().anyMatch(c -> c.type() == chess.cards.CardType.UNDO_MOVE)) s.append("|undo:").append(game.lastMove.positionKey(game.ply));
        return s.toString();
    }
}
