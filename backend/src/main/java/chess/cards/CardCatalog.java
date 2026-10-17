package chess.cards;

import java.util.List;

/**
 * Совместимый фасад фабрики: регистрации и состав колоды определяются аннотациями.
 */
public final class CardCatalog {
    private CardCatalog() {
    }

    public static Card create(int id, CardType type) {
        return CardRegistry.defaultRegistry().create(id, type);
    }

    public static List<Card> newDeck() {
        return CardRegistry.defaultRegistry().newDeck();
    }
}
