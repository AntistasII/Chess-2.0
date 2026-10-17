package chess.pieces;

import chess.game.Action;
import java.util.List;

/**
 * Общее состояние фигуры. Тип задаёт наследник, клетку хранит Board,
 * а правила движения и атак определяют наследники.
 */
public abstract class Piece {
    private final int id;
    private boolean white;
    private boolean moved;
    private boolean boost;
    private boolean shield;
    // Полуход окончания эффекта; 0 — эффекта нет.
    private int frozenUntil;
    private int astralUntil;

    protected Piece(int id, boolean white) {
        this.id = id;
        this.white = white;
    }

    public abstract char type();

    /** Кандидаты на ход; окончательную безопасность короля проверяет игровой движок. */
    public abstract List<Action> moves(MovementContext context, int from);

    /** Атаки отличаются от ходов: например, пешка атакует пустую клетку по диагонали. */
    public abstract boolean attacks(MovementContext context, int from, int target);

    public int id() {
        return id;
    }

    public boolean white() {
        return white;
    }

    public boolean moved() {
        return moved;
    }

    public boolean boost() {
        return boost;
    }

    public boolean shield() {
        return shield;
    }

    public int frozenUntil() {
        return frozenUntil;
    }

    public int astralUntil() {
        return astralUntil;
    }

    /**
     * Заморозка запрещает движение, но сохраняет атаки.
     */
    public boolean frozen() {
        return frozenUntil > 0;
    }

    /**
     * В астрале фигура занимает клетку, но не атакует.
     */
    public boolean astral() {
        return astralUntil > 0;
    }

    public void changeSide(boolean white) {
        this.white = white;
    }

    public void markMoved() {
        moved = true;
    }

    public void enableBoost() {
        boost = true;
    }

    public void protect() {
        shield = true;
    }

    public void freezeUntil(int ply) {
        frozenUntil = ply;
    }

    public void sendToAstralUntil(int ply) {
        astralUntil = ply;
    }

    /**
     * Любое движение, включая перестановку, расходует усиление и щит.
     */
    public void consumeMove() {
        moved = true;
        boost = false;
        shield = false;
    }

    public void expireEffects(int ply) {
        if (frozenUntil > 0 && frozenUntil <= ply) frozenUntil = 0;
        if (astralUntil > 0 && astralUntil <= ply) astralUntil = 0;
    }

    /**
     * Очищение сохраняет сторону и факт движения.
     */
    public void clearEffects() {
        boost = false;
        shield = false;
        frozenUntil = 0;
        astralUntil = 0;
    }

    /**
     * Независимая копия для проверки ходов и поиска ИИ.
     */
    public Piece copy() {
        return copyAs(type());
    }

    /**
     * Копирование и превращение используют один способ переноса состояния.
     */
    protected final Piece copyAs(char type) {
        Piece copy = PieceFactory.create(id, type, white);
        copy.moved = moved;
        copy.boost = boost;
        copy.shield = shield;
        copy.frozenUntil = frozenUntil;
        copy.astralUntil = astralUntil;
        return copy;
    }
}
