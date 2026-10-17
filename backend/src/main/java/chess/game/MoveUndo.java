package chess.game;

import chess.board.Board;
import chess.pieces.Piece;
import java.util.List;

/** Снимок только шахматной части последнего хода: руки, колода и время не откатываются. */
record MoveUndo(Board board, List<Piece> captured, int from, int to, boolean white,
                int enPassant, int halfMoves, boolean wk, boolean wq, boolean bk, boolean bq) {
    static MoveUndo before(Game game, Action action) {
        return new MoveUndo(game.board.copy(), game.captured.stream().map(Piece::copy).toList(),
                action.from(), action.to(), game.whiteTurn, game.enPassant, game.halfMoves,
                game.wKingCastle, game.wQueenCastle, game.bKingCastle, game.bQueenCastle);
    }

    String positionKey(int ply) {
        var key = new StringBuilder().append(from).append(':').append(to).append(':').append(white)
                .append(':').append(enPassant).append(':').append(wk).append(wq).append(bk).append(bq);
        for (Piece p : board) {
            key.append('/');
            if (p != null) key.append(p.type()).append(p.white()).append(p.moved()).append(p.boost()).append(p.shield())
                    .append(Math.max(0, p.frozenUntil() - ply)).append(':').append(Math.max(0, p.astralUntil() - ply));
        }
        key.append('|');
        captured.stream().map(p -> "" + p.type() + p.white()).sorted().forEach(key::append);
        return key.toString();
    }

    void restore(Game game, int freezeUntil) {
        game.board = board.copy();
        game.captured = new java.util.ArrayList<>(captured.stream().map(Piece::copy).toList());
        game.enPassant = enPassant;
        game.halfMoves = halfMoves;
        game.wKingCastle = wk; game.wQueenCastle = wq;
        game.bKingCastle = bk; game.bQueenCastle = bq;
        for (Piece piece : game.board) if (piece != null) piece.expireEffects(game.ply);
        Piece restored = game.board.get(from);
        restored.freezeUntil(Math.max(restored.frozenUntil(), freezeUntil));
    }
}
