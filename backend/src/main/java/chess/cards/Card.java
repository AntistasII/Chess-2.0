package chess.cards;

import java.util.Objects;

/**
 * Неизменяемая карта: идентификатор отличает копии одного заклинания в общей колоде.
 * Выбор целей и применение заклинания выполняет CardRules.
 */
public final class Card {
    private final int id;
    private final CardType type;

    /**
     * Создаёт карту; отрицательные идентификаторы используются для скрытых карт ИИ.
     */
    public Card(int id, CardType type) {
        this.id = id;
        this.type = Objects.requireNonNull(type);
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
        return CardCatalog.name(type);
    }

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
