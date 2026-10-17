package chess.cards;

import chess.game.Action;
import java.util.List;
import java.util.Objects;

/**
 * Неизменяемая карта: идентификатор отличает копии одного заклинания в общей колоде.
 * Наследники определяют допустимые цели и эффект; игровой движок проверяет шах и расходует карту.
 */
public abstract class Card {
    private final int id;
    private final CardType type;
    private final String name;
    private final int durationTurns;

    /**
     * Создаёт карту; отрицательные идентификаторы используются для скрытых карт ИИ.
     */
    protected Card(int id, CardType type, String name) {
        this.id = id;
        this.type = Objects.requireNonNull(type);
        this.name = Objects.requireNonNull(name);
        CardDefinition definition = getClass().getDeclaredAnnotation(CardDefinition.class);
        durationTurns = definition == null ? 0 : definition.durationTurns();
    }

    /**
     * Возвращает идентификатор конкретной карты в партии.
     */
    public int id() {
        return id;
    }

    /**
     * Возвращает вид заклинания.
     */
    public CardType type() {
        return type;
    }

    /**
     * Название для истории ходов и интерфейса.
     */
    public String name() {
        return name;
    }

    public final int durationTurns() { return durationTurns; }

    public final int freezeTurns() {
        CardDefinition definition = getClass().getDeclaredAnnotation(CardDefinition.class);
        return definition == null ? 0 : definition.freezeTurns();
    }

    /** Один ход владельца цели равен двум полуходам партии. */
    protected final int effectEndsAt(int ply) {
        if (durationTurns <= 0) throw new IllegalStateException("Для временной карты задайте durationTurns в @CardDefinition");
        return Math.addExact(ply, Math.multiplyExact(durationTurns, 2));
    }

    /** Кандидаты до проверки безопасности собственного короля. */
    public abstract List<Action> actions(CardContext context);

    /** Эффект уже выбранного законного действия; расход карты выполняет движок. */
    public abstract void apply(CardContext context, Action action);

    // для определения ничьи
    public boolean resetsHalfMoveClock() { return true; }

    /**
     * Сохраняет сравнение по значениям, которое раньше обеспечивал record.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof Card card && id == card.id && type == card.type;
    }

    @Override
    public int hashCode() {
        return 31 * Integer.hashCode(id) + type.hashCode();
    }

    @Override
    public String toString() {
        return "Card[id=" + id + ", type=" + type + "]";
    }
}
