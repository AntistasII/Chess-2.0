package chess.board;

import chess.pieces.Piece;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Прямоугольная доска и размещённые на ней фигуры. По умолчанию имеет размер 8×8.
 * Расширение добавляет пустые линии, сохраняя фигуры, эффекты и исходные стартовые клетки.
 */
public final class Board implements Iterable<Piece> {
    public static final int DEFAULT_WIDTH = 8;
    public static final int DEFAULT_HEIGHT = 8;

    private Geometry geometry;
    private Piece[] cells;

    public Board() {
        this(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * create empty desk
     */
    public Board(int width, int height) {
        geometry = new Geometry(width, height, 0, 0);
        cells = new Piece[Math.multiplyExact(width, height)];
    }

    /**
     * Неизменяемые размеры и координаты безопасно передаются ИИ и интерфейсу.
     */
    public Geometry geometry() {
        return geometry;
    }

    public int width() {
        return geometry.width();
    }

    public int height() {
        return geometry.height();
    }

    public int size() {
        return cells.length;
    }

    public Piece get(int square) {
        geometry.checkSquare(square);
        return cells[square];
    }

    public void set(int square, Piece piece) {
        geometry.checkSquare(square);
        cells[square] = piece;
    }

    /**
     * Убирает фигуры, сохраняя текущие размеры и положение исходной области.
     */
    public void clear() {
        Arrays.fill(cells, null);
    }

    /**
     * Копирует доску вместе с фигурами и их эффектами для независимого расчёта позиции.
     */
    public Board copy() {
        Board copy = new Board(width(), height());
        copy.geometry = geometry;
        copy.cells = Arrays.stream(cells)
                .map(piece -> piece == null ? null : piece.copy())
                .toArray(Piece[]::new);
        return copy;
    }

    /**
     * Добавляет одинаковое количество вертикалей слева и справа.
     */
    public Expansion addColumns(int perSide) {
        return expand(perSide, perSide, 0, 0);
    }

    /**
     * Добавляет одинаковое количество горизонталей снизу и сверху.
     */
    public Expansion addRows(int perSide) {
        return expand(0, 0, perSide, perSide);
    }

    /**
     * Добавляет линии с указанных сторон. Сначала проверяет размеры и создаёт новую сетку,
     * затем переносит прежние фигуры; ошибочный запрос не меняет существующую доску.
     */
    public Expansion expand(int left, int right, int bottom, int top) {

        if (left < 0 || right < 0 || bottom < 0 || top < 0)
            throw new IllegalArgumentException("Количество добавляемых линий не может быть отрицательным");

        Geometry old = geometry;
        Geometry next = new Geometry(
                Math.addExact(Math.addExact(width(), left), right),
                Math.addExact(Math.addExact(height(), bottom), top),
                Math.addExact(old.originFile(), left),
                Math.addExact(old.originRank(), bottom)
        );

        Piece[] expandedCells = new Piece[
                Math.multiplyExact(next.width(), next.height())
                ];
        Expansion expansion = new Expansion(old, next);

        for (int oldIndex = 0; oldIndex < cells.length; oldIndex++) {
            int newIndex = expansion.remap(oldIndex);
            expandedCells[newIndex] = cells[oldIndex];
        }

        cells = expandedCells;
        geometry = next;

        return expansion;
    }

    /**
     * Перебор включает пустые клетки; исходный массив наружу не передаётся.
     */
    @Override
    public Iterator<Piece> iterator() {
        return Arrays.asList(cells).iterator();
    }

    /**
     * Перенос индексов связанных с доской состояний, например взятия на проходе.
     */
    public record Expansion(Geometry before, Geometry after) {
        public int remap(int square) {
            if (square == -1)
                return -1;

            return after.index(before.rank(square) + after.originRank() - before.originRank(),
                    before.file(square) + after.originFile() - before.originFile());
        }
    }
}
