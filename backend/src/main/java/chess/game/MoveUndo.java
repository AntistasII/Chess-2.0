package chess.game;

import chess.board.Board;
import chess.pieces.Piece;
import java.util.List;

/** Снимок только шахматной части последнего хода: руки, колода и время не откатываются. */
record MoveUndo(Board board, List<Piece> captured, int from, int to, boolean white,
                int enPassant, int halfMoves, boolean wk, boolean wq, boolean bk, boolean bq) {
    static MoveUndo before(Game game, Action action) {
        var castling = game.castling();
        return new MoveUndo(game.board().copy(), game.captured().stream().map(Piece::copy).toList(),
                action.from(), action.to(), game.whiteTurn(), game.enPassant(), game.halfMoves(),
                castling.allowed(true, true), castling.allowed(true, false),
                castling.allowed(false, true), castling.allowed(false, false));
    }

    String positionKey(int ply) {
        var key = new StringBuilder().append(from).append(':').append(to).append(':').append(white)
                .append(':').append(enPassant).append(':').append(wk).append(wq).append(bk).append(bq);
        for (Piece p : board) {
            key.append('/');
            if (p != null) key.append(p.type().code()).append(p.white()).append(p.moved()).append(p.boost()).append(p.shield())
                    .append(Math.max(0, p.frozenUntil() - ply)).append(':').append(Math.max(0, p.astralUntil() - ply));
        }
        key.append('|');
        captured.stream().map(p -> "" + p.type().code() + p.white()).sorted().forEach(key::append);
        return key.toString();
    }

    void restore(Game game, int freezeUntil) {
        game.restorePosition(board.copy(), captured.stream().map(Piece::copy).toList());
        game.setEnPassant(enPassant);
        game.setHalfMoves(halfMoves);
        var castling = game.castling();
        castling.set(true, true, wk);
        castling.set(true, false, wq);
        castling.set(false, true, bk);
        castling.set(false, false, bq);
        for (Piece piece : game.board()) if (piece != null) piece.expireEffects(game.ply());
        Piece restored = game.board().get(from);
        restored.freezeUntil(Math.max(restored.frozenUntil(), freezeUntil));
    }
}
