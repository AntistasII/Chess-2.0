package chess.board;

import chess.pieces.Piece;

/** Проверки размеров, вставки линий, переноса координат и независимости копий доски. */
public final class BoardTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void rejects(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Некорректная операция принята");
        } catch (IllegalArgumentException | IndexOutOfBoundsException expected) {
            checks++;
        }
    }

    public static void main(String[] args) {
        Board board = new Board();
        check(board.width() == 8 && board.height() == 8 && board.size() == 64, "Доска по умолчанию 8×8");
        Board rectangle = new Board(10, 12);
        check(rectangle.geometry().index(11, 9) == 119 && rectangle.geometry().square(119).equals("j12"), "Прямоугольная геометрия");
        rejects(() -> new Board(0, 8));
        rejects(() -> board.get(-1));
        rejects(() -> board.set(64, null));
        rejects(() -> board.geometry().index(0, 8));

        Piece pawn = new Piece(1, 'P', true);
        pawn.enableBoost(); pawn.protect(); pawn.freezeUntil(4); pawn.sendToAstralUntil(6);
        board.set(8, pawn);
        Board.Geometry original = board.geometry();
        Board.Expansion columns = board.addColumns(1);
        check(board.width() == 10 && board.height() == 8, "Добавление с двух боков");
        check(columns.remap(8) == 11 && columns.remap(-1) == -1 && board.get(11) == pawn, "Перенос индексов и фигуры");
        check(original.width() == 8 && original.index(1, 0) == 8, "Прежняя геометрия неизменяема");
        check(pawn.boost() && pawn.shield() && pawn.frozenUntil() == 4 && pawn.astralUntil() == 6, "Расширение не расходует эффекты");
        for (int rank = 0; rank < board.height(); rank++)
            check(board.get(board.geometry().index(rank, 0)) == null && board.get(board.geometry().index(rank, 9)) == null, "Новые боковые клетки пусты");

        Board.Expansion rows = board.addRows(2);
        check(board.width() == 10 && board.height() == 12 && rows.remap(11) == 31, "Добавление сверху и снизу");
        check(board.geometry().originalSquare(1, 0) == 31 && board.get(31) == pawn, "Исходная область отслеживается после двух расширений");
        for (int file = 0; file < board.width(); file++)
            check(board.get(board.geometry().index(0, file)) == null && board.get(board.geometry().index(11, file)) == null, "Новые внешние ряды пусты");

        Board copy = board.copy();
        copy.get(31).clearEffects();
        copy.expand(2, 0, 0, 1);
        check(board.width() == 10 && copy.width() == 12 && board.get(31).shield(), "Копирование отделяет геометрию и эффекты");
        check(copy.geometry().originalSquare(1, 0) == 39 && copy.get(39).id() == pawn.id(), "Несимметричное расширение переносит копию");
        rejects(() -> board.expand(-1, 0, 0, 0));
        check(board.width() == 10 && board.height() == 12 && board.get(31) == pawn, "Ошибка расширения не меняет доску");
        check(Board.Geometry.fileName(25).equals("z") && Board.Geometry.fileName(26).equals("aa"), "Подписи вертикалей за пределами z");
        board.clear();
        check(board.size() == 120 && board.get(31) == null, "Очистка сохраняет геометрию");
        System.out.println("Passed " + checks + " board checks.");
    }
}
