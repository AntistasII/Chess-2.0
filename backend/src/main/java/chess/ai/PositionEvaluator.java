package chess.ai;

import chess.pieces.PieceType;
import chess.board.Geometry;
import chess.game.Game;
import chess.pieces.Piece;

/**
 * Оценивает фигуры, эффекты и карты для выбора действия локальным ИИ.
 * Карта атак строится один раз на позицию; по ней считаются угрозы, подвижность,
 * выходы коня и давление на короля. Позиционные веса плавно переходят от миттельшпиля
 * к окончанию по оставшемуся материалу.
 */
final class PositionEvaluator {
    /** Ценность одного места в руке; разница вместимости рук после «Инфляции» и «Шринкфляции». */
    static final int HAND_SLOT = 40;

    private PositionEvaluator() {}

    private static final int[][] KNIGHT = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] DIAGONAL = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] STRAIGHT = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] KING = {{1, 1}, {1, 0}, {1, -1}, {0, 1}, {0, -1}, {-1, 1}, {-1, 0}, {-1, -1}};
    /** Бонус проходной пешки по числу пройденных горизонталей (0–7) на исходной доске. */
    private static final int[] PASSED = {0, 6, 12, 22, 38, 62, 100, 0};
    /** Начальный материал фигур (без пешек) обеих сторон: 2 × (2N + 2B + 2R + Q). */
    private static final double OPENING_PIECES = 6420;

    static int materialValue(PieceType type) {
        return switch (type) {
            case PAWN -> 100;
            case KNIGHT -> 320;
            case BISHOP -> 335;
            case ROOK -> 500;
            case QUEEN -> 900;
            default -> 0;
        };
    }

    /** Положительная оценка означает преимущество выбранной стороны. */
    static double evaluate(Game g, boolean side) {
        Geometry board = g.boardGeometry();
        int size = board.size(), width = board.width(), height = board.height();
        Piece[] cells = new Piece[size];
        int[] whiteAttacks = new int[size], blackAttacks = new int[size];
        int[] whiteLeast = new int[size], blackLeast = new int[size];
        int piecesMaterial = 0;
        int[] whitePawnFiles = new int[width], blackPawnFiles = new int[width];
        int whiteKing = -1, blackKing = -1, whiteBishops = 0, blackBishops = 0;
        for (int s = 0; s < size; s++) {
            Piece p = g.pieceAt(s);
            cells[s] = p;
            if (p == null) continue;
            PieceType type = p.type();
            if (type == PieceType.PAWN) (p.white() ? whitePawnFiles : blackPawnFiles)[board.file(s)]++;
            else if (type == PieceType.KING) { if (p.white()) whiteKing = s; else blackKing = s; }
            else piecesMaterial += materialValue(type);
            if (type == PieceType.BISHOP) { if (p.white()) whiteBishops++; else blackBishops++; }
        }
        double mg = Math.min(1, piecesMaterial / OPENING_PIECES), eg = 1 - mg;
        int[] mobility = new int[size];
        for (int s = 0; s < size; s++) {
            Piece p = cells[s];
            if (p == null || p.astral()) continue;
            int[] attacks = p.white() ? whiteAttacks : blackAttacks;
            int[] least = p.white() ? whiteLeast : blackLeast;
            mobility[s] = markAttacks(cells, board, p, s, attacks, least);
        }
        var effects = g.squareEffects();
        if (!effects.isEmpty())
            for (var e : effects.entrySet()) if (e.getValue().blocksEntry() && e.getKey() < size) {
                // Поле белой дыры недоступно: на нём нельзя ни встать, ни взять.
                whiteAttacks[e.getKey()] = 0;
                blackAttacks[e.getKey()] = 0;
            }

        double score = 0;
        for (int s = 0; s < size; s++) {
            Piece p = cells[s];
            if (p == null) continue;
            boolean white = p.white();
            int[] own = white ? whiteAttacks : blackAttacks, enemy = white ? blackAttacks : whiteAttacks;
            int[] enemyLeast = white ? blackLeast : whiteLeast;
            int file = board.file(s), rank = board.rank(s);
            int relRank = clamp(white ? rank - board.originRank() : board.originRank() + 7 - rank, 0, 7);
            int relFile = clamp(file - board.originFile(), 0, 7);
            double value = materialValue(p.type());
            int advancement = white ? rank : height - 1 - rank;
            // Нормализация сохраняет прежнюю оценку на 8×8 и работает на прямоугольнике.
            double center = 7 - Math.abs((width - 1) / 2.0 - file) * 7 / Math.max(1, width - 1)
                    - Math.abs((height - 1) / 2.0 - rank) * 7 / Math.max(1, height - 1);
            switch (p.type()) {
                case PAWN -> {
                    value += advancement * 9;
                    // Центральные пешки занимают центр в дебюте; фланговые без нужды не двигаем.
                    if (relFile == 3 || relFile == 4) value += mg * (relRank >= 3 && relRank <= 4 ? 14 : relRank == 1 ? -8 : 0);
                    else if (relFile == 2 || relFile == 5) value += mg * (relRank == 3 ? 6 : 0);
                    int[] ownFiles = white ? whitePawnFiles : blackPawnFiles;
                    if (ownFiles[file] > 1) value -= 10 + 8 * eg;
                    boolean leftFriend = file > 0 && ownFiles[file - 1] > 0, rightFriend = file + 1 < width && ownFiles[file + 1] > 0;
                    if (!leftFriend && !rightFriend) value -= 10 + 6 * eg;
                    if (passed(cells, board, s, white)) {
                        double bonus = PASSED[relRank] * (.55 + .9 * eg);
                        // Проходная, которую нельзя остановить фигурой перед ней, ценнее.
                        int ahead = board.inside(rank + (white ? 1 : -1), file) ? board.index(rank + (white ? 1 : -1), file) : -1;
                        if (ahead >= 0 && cells[ahead] != null && cells[ahead].white() != white) bonus *= .6;
                        value += bonus;
                    }
                }
                case KNIGHT -> {
                    value += center * 7;
                    if (!p.frozen() && !p.astral()) value += knightExits(cells, board, s, white, enemy) + 3 * (mobility[s] - 4);
                    if (relRank == 0 && !p.moved()) value -= 16 * mg;
                }
                case BISHOP -> {
                    value += center * 7 + 3 * (mobility[s] - 6);
                    if (relRank == 0 && !p.moved()) value -= 14 * mg;
                }
                case ROOK -> {
                    value += 2 * (mobility[s] - 6);
                    int[] ownFiles = white ? whitePawnFiles : blackPawnFiles, enemyFiles = white ? blackPawnFiles : whitePawnFiles;
                    if (ownFiles[file] == 0) value += enemyFiles[file] == 0 ? 22 : 11;
                    if (relRank == 6) value += 16;
                }
                case QUEEN -> {
                    value += mobility[s] - 10;
                    // Ранний выход ферзя до развития лёгких фигур — лёгкая мишень.
                    if (p.moved() && undeveloped(cells, board, white) >= 2) value -= 22 * mg;
                }
                case KING -> {
                    // Укрытие пешками полезно при наличии тяжёлых фигур у противника.
                    for (int df = -1; df <= 1; df++) {
                        int r = rank + (white ? 1 : -1), f = file + df;
                        if (!board.inside(r, f)) continue;
                        Piece shelter = cells[board.index(r, f)];
                        if (shelter != null && shelter.white() == white && shelter.type() == PieceType.PAWN) value += 10;
                    }
                    value += KingSafety.evaluate(g, p, s);
                    // Давление на клетки вокруг короля, пока у противника есть ферзь или ладьи.
                    int pressure = 0;
                    for (int[] d : KING) {
                        int r = rank + d[0], f = file + d[1];
                        if (board.inside(r, f)) pressure += Math.min(2, enemy[board.index(r, f)]);
                    }
                    value -= pressure * 7 * mg;
                    // В окончании король — активная фигура.
                    value += center * 5 * eg;
                }
                default -> {}
            }
            if (p.boost()) value += p.type() == PieceType.KING ? 15 : 22;
            if (p.shield()) value += 18;
            if (p.frozen()) value -= value * .18;
            if (p.astral()) value -= value * .25;
            // Не оставляем фигуры под бесплатным взятием, даже на границе глубины поиска.
            if (p.type() != PieceType.KING && !p.astral() && enemy[s] > 0) {
                boolean defended = own[s] > 0;
                double base = materialValue(p.type());
                double penalty = base * (defended ? .06 : .45);
                // Нападение более дешёвой фигурой опасно и при защите.
                if (defended && enemyLeast[s] > 0 && enemyLeast[s] < base) penalty = Math.max(penalty, (base - enemyLeast[s]) * .3);
                value -= penalty;
            }
            score += (white == side ? 1 : -1) * value;
        }
        if (whiteBishops >= 2) score += side ? 30 : -30;
        if (blackBishops >= 2) score += side ? -30 : 30;
        // Пространство и контроль центра.
        for (int s = 0; s < size; s++) {
            int rank = board.rank(s) - board.originRank(), file = board.file(s) - board.originFile();
            if (rank < 2 || rank > 5 || file < 2 || file > 5) continue;
            int control = Integer.signum(whiteAttacks[s] - blackAttacks[s]);
            score += (side ? 1 : -1) * control * 3 * mg;
        }
        for (boolean white : new boolean[]{true, false}) {
            double handValue = 0;
            for (var c : g.cards(white))
                handValue += switch (c.type()) {
                    case CONVERT -> 90;
                    case UNDO_MOVE -> 70;
                    case DESTROY_CARD -> g.cards(!white).isEmpty() ? 5 : 55;
                    case TRAP, HIDDEN_TRAP -> 35;
                    case WHITE_HOLE -> 45;
                    case SACRIFICE -> 12;
                    case EXPAND_BOTTOM, EXPAND_TOP, EXPAND_LEFT, EXPAND_RIGHT -> 15;
                    case ASTRAL -> 40;
                    case FREEZE -> 25;
                    case SHIELD -> 28;
                    case PAWN_BOOST -> 25;
                    case KING_BOOST -> 18;
                    case REVIVE_PAWN -> g.capturedPieces().stream().anyMatch(p -> p.white() == white && p.type() == PieceType.PAWN) ? 65 : 12;
                    case REVIVE_PIECE ->
                            g.capturedPieces().stream().filter(p -> p.white() == white && p.type() != PieceType.PAWN).mapToInt(p -> p.type() == PieceType.QUEEN ? 350 : p.type() == PieceType.ROOK ? 230 : 140).max().orElse(12);
                    case CLEANSE -> 30;
                    case SWAP -> 38;
                    case SCOUT -> 10;
                    case EXCHANGE -> 15;
                    case TRANSFORM -> 20;
                    case INFLATION, SHRINKFLATION -> 25;
                    default -> 45;
                };
            // Место в руке стоит примерно как карта, которую можно будет набрать.
            handValue += g.handLimit(white) * HAND_SLOT;
            score += (white == side ? 1 : -1) * handValue;
        }
        score += EndgameConversion.evaluate(g, side) - EndgameConversion.evaluate(g, !side);
        if (g.inCheck(side)) score -= 75;
        if (g.inCheck(!side)) score += EndgameConversion.active(g, side) ? 25 : 75;
        return score;
    }

    /** Отмечает атакованные клетки и возвращает число доступных фигуре клеток. */
    private static int markAttacks(Piece[] cells, Geometry board, Piece p, int s, int[] attacks, int[] least) {
        int rank = board.rank(s), file = board.file(s), reach = 0;
        int value = p.type() == PieceType.KING ? 2000 : materialValue(p.type());
        switch (p.type()) {
            case PAWN -> {
                int r = rank + (p.white() ? 1 : -1);
                for (int df : new int[]{-1, 1}) if (board.inside(r, file + df)) mark(board.index(r, file + df), value, attacks, least);
            }
            case KNIGHT -> reach = jumps(cells, board, p, rank, file, KNIGHT, value, attacks, least);
            case KING -> jumps(cells, board, p, rank, file, KING, value, attacks, least);
            case BISHOP -> reach = rays(cells, board, p, rank, file, DIAGONAL, value, attacks, least);
            case ROOK -> reach = rays(cells, board, p, rank, file, STRAIGHT, value, attacks, least);
            case QUEEN -> reach = rays(cells, board, p, rank, file, DIAGONAL, value, attacks, least)
                    + rays(cells, board, p, rank, file, STRAIGHT, value, attacks, least);
            default -> {}
        }
        return reach;
    }

    private static int jumps(Piece[] cells, Geometry board, Piece p, int rank, int file, int[][] dirs, int value, int[] attacks, int[] least) {
        int reach = 0;
        for (int[] d : dirs) {
            int r = rank + d[0], f = file + d[1];
            if (!board.inside(r, f)) continue;
            int t = board.index(r, f);
            mark(t, value, attacks, least);
            if (cells[t] == null || cells[t].white() != p.white()) reach++;
        }
        return reach;
    }

    private static int rays(Piece[] cells, Geometry board, Piece p, int rank, int file, int[][] dirs, int value, int[] attacks, int[] least) {
        int reach = 0;
        for (int[] d : dirs) {
            int r = rank + d[0], f = file + d[1];
            while (board.inside(r, f)) {
                int t = board.index(r, f);
                mark(t, value, attacks, least);
                Piece blocker = cells[t];
                if (blocker == null || blocker.white() != p.white()) reach++;
                if (blocker != null) break;
                r += d[0];
                f += d[1];
            }
        }
        return reach;
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private static void mark(int t, int value, int[] attacks, int[] least) {
        attacks[t]++;
        if (least[t] == 0 || value < least[t]) least[t] = value;
    }

    private static boolean passed(Piece[] cells, Geometry board, int s, boolean white) {
        int rank = board.rank(s), file = board.file(s), step = white ? 1 : -1;
        for (int r = rank + step; r >= 0 && r < board.height(); r += step)
            for (int f = file - 1; f <= file + 1; f++) {
                if (!board.inside(r, f)) continue;
                Piece q = cells[board.index(r, f)];
                if (q != null && q.type() == PieceType.PAWN && q.white() != white) return false;
            }
        return true;
    }

    /** Безопасные выходы коня: без них фигура может попасть в ловушку. */
    private static double knightExits(Piece[] cells, Geometry board, int s, boolean white, int[] enemy) {
        int rank = board.rank(s), file = board.file(s), exits = 0;
        // Это оценка текущих атак, не доказательство безопасности всей будущей линии.
        for (int[] d : KNIGHT) {
            int r = rank + d[0], f = file + d[1];
            if (!board.inside(r, f)) continue;
            int t = board.index(r, f);
            Piece victim = cells[t];
            if (victim != null && victim.white() == white) continue;
            if (enemy[t] == 0 || victim != null && materialValue(victim.type()) >= 320) exits++;
        }
        return exits == 0 ? -90 : exits == 1 ? -20 : 0;
    }

    private static int undeveloped(Piece[] cells, Geometry board, boolean white) {
        int count = 0;
        int home = board.originRank() + (white ? 0 : 7);
        for (int f = 0; f < board.width(); f++) {
            if (!board.inside(home, f)) continue;
            Piece q = cells[board.index(home, f)];
            if (q != null && q.white() == white && (q.type() == PieceType.KNIGHT || q.type() == PieceType.BISHOP) && !q.moved()) count++;
        }
        return count;
    }
}
