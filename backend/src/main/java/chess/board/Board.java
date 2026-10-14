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
     * Создаёт пустую доску заданного размера.
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

    /**
     * Читает или размещает фигуру; null означает свободную клетку.
     */
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
        for (int square = 0; square < size(); square++)
            if (cells[square] != null) copy.cells[square] = cells[square].copy();
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
        Geometry next = new Geometry(Math.addExact(Math.addExact(width(), left), right),
                Math.addExact(Math.addExact(height(), bottom), top),
                Math.addExact(old.originFile(), left), Math.addExact(old.originRank(), bottom));
        Piece[] expanded = new Piece[Math.multiplyExact(next.width(), next.height())];
        Expansion expansion = new Expansion(old, next);
        for (int square = 0; square < size(); square++) expanded[expansion.remap(square)] = cells[square];
        cells = expanded;
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
     * Координаты клеток текущей сетки. Индекс = ряд × ширина + вертикаль.
     * originFile/originRank отслеживают смещение исходного a1 после добавления линий.
     */
    public record Geometry(int width, int height, int originFile, int originRank) {
        public Geometry {
            if (width <= 0 || height <= 0 || originFile < 0 || originRank < 0
                    || originFile >= width || originRank >= height)
                throw new IllegalArgumentException("Некорректные размеры или начало доски");
            Math.multiplyExact(width, height);
        }

        public int size() {
            return width * height;
        }

        public boolean inside(int rank, int file) {
            return rank >= 0 && rank < height && file >= 0 && file < width;
        }

        /**
         * Преобразование координат проверяет границы, не допуская перехода на соседний ряд.
         */
        public int index(int rank, int file) {
            if (!inside(rank, file))
                throw new IndexOutOfBoundsException("Клетка за пределами доски");
            return rank * width + file;
        }

        public int rank(int square) {
            checkSquare(square);
            return square / width;
        }

        public int file(int square) {
            checkSquare(square);
            return square % width;
        }

        /**
         * Стартовая клетка исходной позиции с учётом вставок слева и снизу.
         */
        public int originalSquare(int rank, int file) {
            return index(originRank + rank, originFile + file);
        }

        /**
         * Шахматная подпись текущей клетки, включая вертикали за пределами h.
         */
        public String square(int square) {
            return fileName(file(square)) + (rank(square) + 1);
        }

        /**
         * Буквенные подписи: a…z, aa…az и далее.
         */
        public static String fileName(int file) {
            if (file < 0) throw new IllegalArgumentException("Номер вертикали не может быть отрицательным");
            StringBuilder name = new StringBuilder();
            long value = (long) file + 1;
            while (value > 0) {
                value--;
                name.append((char) ('a' + value % 26));
                value /= 26;
            }
            return name.reverse().toString();
        }

        private void checkSquare(int square) {
            if (square < 0 || square >= size()) throw new IndexOutOfBoundsException("Клетка за пределами доски");
        }
    }

    /**
     * Перенос индексов связанных с доской состояний, например взятия на проходе.
     */
    public record Expansion(Geometry before, Geometry after) {
        public int remap(int square) {
            if (square == -1) return -1;
            return after.index(before.rank(square) + after.originRank() - before.originRank(),
                    before.file(square) + after.originFile() - before.originFile());
        }
    }
}
