package chess.game;

/** Вид действия за ход: обычный ход фигурой, применение карты или добор из колоды. */
public enum ActionKind {
    MOVE("move"), CARD("card"), DRAW("draw");

    private final String id;

    ActionKind(String id) { this.id = id; }

    /** Значение в JSON для интерфейса. */
    public String id() { return id; }
}
