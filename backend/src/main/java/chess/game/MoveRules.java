package chess.game;

import chess.board.Board;

import chess.pieces.Piece;
import java.util.*;

/** Правила движения, атаки, рокировки и исполнения шахматного хода. */
final class MoveRules {
    private final Game game;
    private final Board board;

    MoveRules(Game game) { this.game = game; this.board = game.board; }

    /** Генерирует ходы без итоговой проверки шаха, учитывая эффекты и занятые клетки. */
    List<Action> moves() {
        var out = new ArrayList<Action>();
        for (int s = 0; s < board.size(); s++) {
            Piece p = board.get(s);
            if (p == null || p.white() != game.whiteTurn || p.astral() || p.frozen()) continue;
            int r = board.geometry().rank(s), f = board.geometry().file(s);
            if (p.type() == 'P') {
                int dir = p.white() ? 1 : -1, start = game.pawnStartRank(p.white());
                int max = p.boost() ? (!p.moved() && r == start ? 3 : 2) : (!p.moved() && r == start ? 2 : 1);
                for (int n = 1; n <= max; n++) {
                    int nr = r + dir * n;
                    if (!board.geometry().inside(nr, f) || board.get(board.geometry().index(nr, f)) != null) break;
                    addMove(out, s, board.geometry().index(nr, f));
                }
                for (int df : new int[]{-1, 1})
                    if (board.geometry().inside(r + dir, f + df)) {
                        int to = board.geometry().index(r + dir, f + df);
                        Piece target = board.get(to);
                        if (target != null && !target.astral() && target.white() != p.white() && target.type() != 'K')
                            addMove(out, s, to);
                        if (to == game.enPassant && target == null) {
                            Piece victim = board.get(to - dir * board.width());
                            if (victim != null && victim.type() == 'P' && victim.white() != p.white() && !victim.astral())
                                addMove(out, s, to);
                        }
                    }
            } else if (p.type() == 'N') {
                for (int[] d : new int[][]{{1, 2}, {1, -2}, {-1, 2}, {-1, -2}, {2, 1}, {2, -1}, {-2, 1}, {-2, -1}}) {
                    if (board.geometry().inside(r + d[0], f + d[1]) && available(board.geometry().index(r + d[0], f + d[1]), p.white()))
                        addMove(out, s, board.geometry().index(r + d[0], f + d[1]));
                }
            } else {
                int max = p.type() == 'K' ? (p.boost() ? 2 : 1) : Math.max(board.width(), board.height()) - 1;
                for (int dr = -1; dr <= 1; dr++)
                    for (int df = -1; df <= 1; df++) {
                        if (dr == 0 && df == 0 || p.type() == 'B' && (dr == 0 || df == 0) || p.type() == 'R' && dr != 0 && df != 0)
                            continue;
                        for (int n = 1; n <= max; n++) {
                            int nr = r + dr * n, nf = f + df * n;
                            if (!board.geometry().inside(nr, nf)) break;
                            int to = board.geometry().index(nr, nf);
                            if (!available(to, p.white())) break;
                            boolean safe = true;
                            // Усиленный король не может пересечь атакуемую промежуточную клетку.
                            if (p.type() == 'K' && n == 2) {
                                Game mid = game.copy();
                                mid.board.set(s, null);
                                mid.board.set(board.geometry().index(r + dr, f + df), p.copy());
                                safe = !mid.inCheck(p.white());
                            }
                            if (safe) addMove(out, s, to);
                            if (board.get(to) != null) break;
                        }
                    }
                if (p.type() == 'K') addCastles(out, s, p);
            }
        }
        return out;
    }

    /** Проверяет геометрию атак: замороженные фигуры атакуют, фигуры в астрале — нет. */
    boolean attacked(int target, boolean byWhite) {
        for (int s = 0; s < board.size(); s++) {
            Piece p = board.get(s);
            if (p == null || p.white() != byWhite || p.astral()) continue;
            int dr = board.geometry().rank(target) - board.geometry().rank(s), df = board.geometry().file(target) - board.geometry().file(s), ar = Math.abs(dr), af = Math.abs(df);
            switch (p.type()) {
                case 'P':
                    if (dr == (p.white() ? 1 : -1) && af == 1) return true;
                    break;
                case 'N':
                    if (ar * af == 2) return true;
                    break;
                case 'K':
                    if (Math.max(ar, af) <= (p.boost() ? 2 : 1) && Math.max(ar, af) > 0 && (ar == 0 || af == 0 || ar == af) && clearRay(s, target))
                        return true;
                    break;
                case 'B':
                    if (ar == af && ar > 0 && clearRay(s, target)) return true;
                    break;
                case 'R':
                    if ((dr == 0 || df == 0) && ar + af > 0 && clearRay(s, target)) return true;
                    break;
                case 'Q':
                    if ((dr == 0 || df == 0 || ar == af) && ar + af > 0 && clearRay(s, target)) return true;
                    break;
                default:
                    break;
            }
        }
        return false;
    }

    /** Любая фигура, включая астральную, перекрывает промежуточные клетки луча. */
    boolean clearRay(int from, int to) {
        int dr = Integer.signum(board.geometry().rank(to) - board.geometry().rank(from)), df = Integer.signum(board.geometry().file(to) - board.geometry().file(from));
        int r = board.geometry().rank(from) + dr, f = board.geometry().file(from) + df;
        while (r != board.geometry().rank(to) || f != board.geometry().file(to)) {
            if (!board.geometry().inside(r, f) || board.get(board.geometry().index(r, f)) != null) return false;
            r += dr;
            f += df;
        }
        return true;
    }

    /** Разрешает свободную клетку или взятие врага; короля и фигуру в астрале брать нельзя. */
    boolean available(int to, boolean white) {
        Piece p = board.get(to);
        return p == null || !p.astral() && p.white() != white && p.type() != 'K';
    }

    /** На последнем ряду создаёт четыре варианта превращения пешки. */
    private void addMove(List<Action> out, int from, int to) {
        if (board.get(from).type() == 'P' && (board.geometry().rank(to) == 0 || board.geometry().rank(to) == board.height() - 1))
            for (String p : List.of("Q", "R", "B", "N")) out.add(Action.move(from, to, p));
        else out.add(Action.move(from, to, ""));
    }

    /** Проверяет права, ладью, свободный путь и безопасность промежуточной клетки короля. */
    private void addCastles(List<Action> out, int s, Piece king) {
        int kingSquare = game.kingStartSquare(king.white());
        if (s != kingSquare || king.moved() || game.inCheck(king.white())) return;
        for (boolean shortSide : new boolean[]{true, false}) {
            boolean right = king.white() ? (shortSide ? game.wKingCastle : game.wQueenCastle) : (shortSide ? game.bKingCastle : game.bQueenCastle);
            int rookSquare = game.rookStartSquare(king.white(), shortSide);
            Piece rook = board.get(rookSquare);
            if (!right || rook == null || rook.type() != 'R' || rook.white() != king.white() || rook.moved() || rook.astral() || rook.frozen())
                continue;
            int step = shortSide ? 1 : -1;
            boolean clear = true;
            for (int t = s + step; t != rookSquare; t += step) if (board.get(t) != null) clear = false;
            Game mid = game.copy();
            mid.board.set(s, null);
            mid.board.set(s + step, king.copy());
            if (clear && !mid.inCheck(king.white())) out.add(Action.move(s, s + step * 2, "castle"));
        }
    }

    /** Обновляет доску, взятия, рокировку, превращение и право взятия на проходе. */
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
        if (p.type() == 'P' && Math.abs(board.geometry().rank(a.to()) - board.geometry().rank(a.from())) == 2) game.enPassant = (a.from() + a.to()) / 2;
        if (!a.option().isEmpty() && !a.option().equals("castle")) p.promoteTo(a.option().charAt(0));
        p.consumeMove();

    }

}
