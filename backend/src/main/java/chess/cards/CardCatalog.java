package chess.cards;

import java.util.*;

/** Названия карт и состав общей колоды; количество копий не зависит от порядка перечисления. */
public final class CardCatalog {
    private CardCatalog() {}

    /** Русское название заклинания для интерфейса. */
    static String name(CardType t) {
        return switch (t) {
            case PAWN_BOOST -> "Усиление пешки";
            case KING_BOOST -> "Усиление короля";
            case REVIVE_PAWN -> "Возрождение пешки";
            case REVIVE_PIECE -> "Возрождение фигуры";
            case CONVERT -> "Смена стороны";
            case FREEZE -> "Заморозка";
            case ASTRAL -> "Астрал";
            case SHIELD -> "Щит";
            case CLEANSE -> "Очищение";
            case SWAP -> "Перестановка";
            case SCOUT -> "Разведка";
            case EXCHANGE -> "Обмен планами";
            default -> "Скрытая карта";
        };
    }

    /** Создаёт отдельные экземпляры карт с уникальными идентификаторами и перемешивает колоду. */
    public static List<Card> newDeck() {
        var cards = new ArrayList<Card>();
        int id = 1;
        var entries = List.of(
            Map.entry(CardType.PAWN_BOOST, 5),
            Map.entry(CardType.KING_BOOST, 2),
            Map.entry(CardType.REVIVE_PAWN, 4),
            Map.entry(CardType.REVIVE_PIECE, 2),
            Map.entry(CardType.CONVERT, 2),
            Map.entry(CardType.FREEZE, 3),
            Map.entry(CardType.ASTRAL, 2),
            Map.entry(CardType.SHIELD, 3),
            Map.entry(CardType.CLEANSE, 2),
            Map.entry(CardType.SWAP, 2),
            Map.entry(CardType.SCOUT, 2),
            Map.entry(CardType.EXCHANGE, 1)
        );
        for (var entry : entries) for (int n = 0; n < entry.getValue(); n++) cards.add(new Card(id++, entry.getKey()));
        Collections.shuffle(cards);
        return cards;
    }
}
