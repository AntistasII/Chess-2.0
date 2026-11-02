package chess.game;

import chess.board.Board;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** Поля заклинаний на клетках доски: капканы и белые дыры со сроком действия. */
final class SquareEffects {
    private Map<Integer, SquareEffect> effects = new HashMap<>();

    SquareEffect at(int square) { return effects.get(square); }

    boolean isEmpty() { return effects.isEmpty(); }

    /** Белая дыра запрещает вход на клетку. */
    boolean blocksEntry(int square) {
        SquareEffect effect = effects.get(square);
        return effect != null && effect.blocksEntry();
    }

    boolean visibleAt(int square, boolean viewer) {
        SquareEffect effect = effects.get(square);
        return effect != null && effect.visibleTo(viewer);
    }

    void place(int square, SquareEffect effect) { effects.put(square, effect); }

    /** Поля по возрастанию клетки, только для чтения. */
    Map<Integer, SquareEffect> sorted() { return Collections.unmodifiableMap(new TreeMap<>(effects)); }

    /** Убирает поля, которые не видит этот игрок (невидимые капканы соперника). */
    void hideFrom(boolean viewer) { effects.values().removeIf(effect -> !effect.visibleTo(viewer)); }

    void expire(int ply) { effects.values().removeIf(effect -> effect.expiresAt() <= ply); }

    /** После расширения доски номера клеток сдвигаются. */
    void remap(Board.Expansion expansion) {
        var remapped = new HashMap<Integer, SquareEffect>();
        effects.forEach((square, effect) -> remapped.put(expansion.remap(square), effect));
        effects = remapped;
    }

    void clear() { effects.clear(); }

    SquareEffects copy() {
        var copy = new SquareEffects();
        copy.effects = new HashMap<>(effects);
        return copy;
    }
}
