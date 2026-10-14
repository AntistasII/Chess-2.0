package chess.pieces;

/**
 * Шахматная фигура: хранит тип, сторону, факт движения и эффекты карт.
 * Клетку хранит доска Game, а правила движения определяет MoveRules.
 */
public final class Piece {
    private final int id;
    // P — пешка, N — конь, B — слон, R — ладья, Q — ферзь, K — король.
    private char type;
    private boolean white;
    private boolean moved;
    private boolean boost;
    private boolean shield;
    // Номер полухода окончания эффекта; ноль означает отсутствие эффекта.
    private int frozenUntil;
    private int astralUntil;

    /**
     * Идентификатор фигуры сохраняется при копировании позиции.
     */
    public int id() {
        return id;
    }

    /**
     * Текущий шахматный тип; может измениться при превращении пешки.
     */
    public char type() {
        return type;
    }

    /**
     * Сторона, которой сейчас принадлежит фигура.
     */
    public boolean white() {
        return white;
    }

    /**
     * Факт движения влияет на первый ход пешки и право рокировки.
     */
    public boolean moved() {
        return moved;
    }

    /**
     * Усиление и щит расходуются при любом следующем движении.
     */
    public boolean boost() {
        return boost;
    }

    public boolean shield() {
        return shield;
    }

    /**
     * Полуход окончания временных эффектов; ноль означает отсутствие эффекта.
     */
    public int frozenUntil() {
        return frozenUntil;
    }

    public int astralUntil() {
        return astralUntil;
    }

    /**
     * Накладывает усиление без движения фигуры.
     */
    public void enableBoost() {
        boost = true;
    }

    /**
     * Защищает от вражеских заклинаний до следующего движения.
     */
    public void protect() {
        shield = true;
    }

    /**
     * Задаёт момент окончания заморозки.
     */
    public void freezeUntil(int ply) {
        frozenUntil = ply;
    }

    /**
     * Задаёт момент возвращения из астрала.
     */
    public void sendToAstralUntil(int ply) {
        astralUntil = ply;
    }

    /**
     * Меняет владельца; направление движения пешки определяется её стороной.
     */
    public void changeSide(boolean white) {
        this.white = white;
    }

    /**
     * Отмечает движение без расходования эффектов, например при смене стороны.
     */
    public void markMoved() {
        moved = true;
    }

    /**
     * Обновляет тип при превращении пешки; допустимые варианты проверяет игровое ядро.
     */
    public void promoteTo(char type) {
        this.type = type;
    }

    /**
     * Новая фигура появляется без эффектов и без выполненных ходов.
     */
    public Piece(int id, char type, boolean white) {
        this.id = id;
        this.type = type;
        this.white = white;
    }

    /**
     * Независимая копия для проверки законности и поиска ИИ.
     */
    public Piece copy() {
        var piece = new Piece(id, type, white);
        piece.moved = moved;
        piece.boost = boost;
        piece.shield = shield;
        piece.frozenUntil = frozenUntil;
        piece.astralUntil = astralUntil;
        return piece;
    }

    /**
     * Фигура в астрале остаётся на зарезервированной клетке, но не атакует.
     */
    public boolean astral() {
        return astralUntil > 0;
    }

    /**
     * Заморозка запрещает движение, но сохраняет атаки фигуры.
     */
    public boolean frozen() {
        return frozenUntil > 0;
    }

    /**
     * Любое движение, включая перестановку, расходует усиление и щит.
     */
    public void consumeMove() {
        moved = true;
        boost = false;
        shield = false;
    }

    /**
     * Завершает временные эффекты после окончания соответствующего полухода.
     */
    public void expireEffects(int ply) {
        if (frozenUntil > 0 && frozenUntil <= ply) frozenUntil = 0;
        if (astralUntil > 0 && astralUntil <= ply) astralUntil = 0;
    }

    /**
     * Очищение снимает эффекты, сохраняя сторону и факт прежнего движения.
     */
    public void clearEffects() {
        boost = false;
        shield = false;
        frozenUntil = 0;
        astralUntil = 0;
    }
}
