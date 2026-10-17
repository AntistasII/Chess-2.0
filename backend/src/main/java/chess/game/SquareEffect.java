package chess.game;

/** Неизменяемое поле заклинания; сроки хранятся в полуходах партии. */
public record SquareEffect(Type type, boolean white, int expiresAt, int freezeTurns) {
    public enum Type { TRAP, HIDDEN_TRAP, WHITE_HOLE }
    public boolean visibleTo(boolean viewer) { return type != Type.HIDDEN_TRAP || white == viewer; }
    public boolean blocksEntry() { return type == Type.WHITE_HOLE; }
}
