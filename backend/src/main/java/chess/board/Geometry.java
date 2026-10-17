package chess.board;

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

    void checkSquare(int square) {
        if (square < 0 || square >= size()) throw new IndexOutOfBoundsException("Клетка за пределами доски");
    }
}

