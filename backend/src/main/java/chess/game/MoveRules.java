package chess.game;

import chess.board.Board;

import chess.pieces.Piece;
import chess.pieces.Pawn;
import chess.pieces.King;
import chess.pieces.MovementContext;
import chess.board.Geometry;

import java.util.*;

/**
 * Собирает ходы и атаки фигур и применяет выбранный шахматный ход к партии.
 */
final class MoveRules {
    private final Game game;
    private final Board board;

    MoveRules(Game game) {
        this.game = game;
        this.board = game.board;
    }

    /**
     * Фигура сама генерирует ходы; общий запрет движения проверяется здесь.
     */
    List<Action> moves() {
        var moves = new ArrayList<Action>();
        var context = new Context(game);
        for (int square = 0; square < board.size(); square++) {
            Piece piece = board.get(square);
            if (piece == null || piece.white() != game.whiteTurn || piece.astral() || piece.frozen()) continue;
            moves.addAll(piece.moves(context, square));
        }
        return moves;
    }

    /** Варианты одной фигуры, включая другую сторону; безопасность короля проверяет TurnRules. */
    List<Action> movesFrom(int square) {
        Piece piece = board.get(square);
        if (piece == null || piece.astral() || piece.frozen()) return List.of();
        return piece.moves(new Context(game), square);
    }

    /**
     * Заморозка сохраняет атаки, астрал исключает их.
     */
    boolean attacked(int target, boolean byWhite) {
        if (game.blocked(target)) return false;
        var context = new Context(game);
        for (int square = 0; square < board.size(); square++) {
            Piece piece = board.get(square);
            if (piece != null && piece.white() == byWhite && !piece.astral()
                    && piece.attacks(context, square, target)) return true;
        }
        return false;
    }

    /**
     * Адаптер передаёт правилам фигур данные позиции без доступа к самой сессии.
     */
    private record Context(Game game) implements MovementContext {
        public boolean blocked(int square) { return game.blocked(square); }

        public Geometry geometry() {
            return game.boardGeometry();
        }

        public Piece pieceAt(int square) {
            return game.board.get(square);
        }

        public int pawnStartRank(boolean white) {
            return game.pawnStartRank(white);
        }

        public int enPassant() {
            return game.enPassant;
        }

        public int kingStartSquare(boolean white) {
            return game.kingStartSquare(white);
        }

        public int rookStartSquare(boolean white, boolean shortSide) {
            return game.rookStartSquare(white, shortSide);
        }

        public boolean castleAllowed(boolean white, boolean shortSide) {
            return white ? (shortSide ? game.wKingCastle : game.wQueenCastle)
                    : (shortSide ? game.bKingCastle : game.bQueenCastle);
        }

        public boolean kingSafeAt(King king, int from, int to) {
            if (from == to) return !game.inCheck(king.white());
            Game intermediate = game.copy();
            intermediate.board.set(from, null);
            intermediate.board.set(to, king.copy());
            return !intermediate.inCheck(king.white());
        }
    }

    /**
     * Обновляет доску, взятия, рокировку, превращение и право взятия на проходе.
     */
    void apply(Action a, int oldEp) {
        Piece p = board.get(a.from());
        Piece victim = board.get(a.to());
        // При взятии на проходе жертва стоит не на конечной клетке хода.
        if (p.type() == 'P' && a.to() == oldEp && victim == null && board.geometry().file(a.from()) != board.geometry().file(a.to())) {
            int square = a.to() + (p.white() ? -board.width() : board.width());
            victim = board.get(square);
            board.set(square, null);
        }
        game.halfMoves = p.type() == 'P' || victim != null ? 0 : game.halfMoves + 1;
        if (victim != null) game.capture(victim, a.to());
        game.disableCastle(p, a.from());
        board.set(a.from(), null);
        board.set(a.to(), p);
        if (a.option().equals("castle")) {
            int rookFrom = game.rookStartSquare(p.white(), a.to() > a.from()), rookTo = a.from() + (a.to() > a.from() ? 1 : -1);
            Piece rook = board.get(rookFrom);
            board.set(rookFrom, null);
            board.set(rookTo, rook);
            rook.consumeMove();
        }
        if (p.type() == 'P' && Math.abs(board.geometry().rank(a.to()) - board.geometry().rank(a.from())) == 2)
            game.enPassant = (a.from() + a.to()) / 2;
        if (!a.option().isEmpty() && !a.option().equals("castle")) {
            if (!(p instanceof Pawn pawn)) throw new IllegalArgumentException("Превращаться может только пешка");
            p = pawn.promoteTo(a.option().charAt(0));
            board.set(a.to(), p);
        }
        p.consumeMove();

    }

}
