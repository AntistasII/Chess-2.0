package chess.game;

import chess.cards.Card;
import chess.cards.CardCatalog;
import chess.cards.CardType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Руки обоих игроков с их вместимостью, общая колода и сброс. Карты неизменяемы,
 * поэтому копируются только списки.
 *
 * <p>Вместимость руки у каждого игрока своя: её меняют «Инфляция» и «Шринкфляция».
 * Если вместимость стала меньше числа карт, лишние карты остаются в руке, но добор
 * недоступен, пока карт не станет меньше вместимости.</p>
 */
final class CardPiles {
    private final List<Card> whiteHand, blackHand, deck, discard;
    private int whiteLimit, blackLimit;

    CardPiles(int handLimit) {
        this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), handLimit, handLimit);
    }

    private CardPiles(List<Card> whiteHand, List<Card> blackHand, List<Card> deck, List<Card> discard,
                      int whiteLimit, int blackLimit) {
        this.whiteHand = whiteHand;
        this.blackHand = blackHand;
        this.deck = deck;
        this.discard = discard;
        setLimit(true, whiteLimit);
        setLimit(false, blackLimit);
    }

    List<Card> hand(boolean white) { return white ? whiteHand : blackHand; }

    /** Вместимость руки игрока. */
    int limit(boolean white) { return white ? whiteLimit : blackLimit; }

    void setLimit(boolean white, int limit) {
        if (limit < GameSettings.MIN_HAND_LIMIT || limit > GameSettings.MAX_HAND_LIMIT)
            throw new IllegalArgumentException("Лимит карт: от 1 до 12");
        if (white) whiteLimit = limit; else blackLimit = limit;
    }

    /** В руке есть свободное место и колоду можно пополнить. */
    boolean canDraw(boolean white) { return hand(white).size() < limit(white) && canRefill(); }

    List<Card> deck() { return deck; }

    List<Card> discard() { return discard; }

    boolean holds(boolean white, CardType type) {
        for (Card card : hand(white)) if (card.type() == type) return true;
        return false;
    }

    /** Колода или сброс ещё содержат карты. */
    boolean canRefill() { return !deck.isEmpty() || !discard.isEmpty(); }

    /** Добор верхней карты; пустая колода пополняется перемешанным сбросом. */
    void draw(boolean white) {
        if (deck.isEmpty()) {
            deck.addAll(discard);
            discard.clear();
            Collections.shuffle(deck);
        }
        hand(white).add(deck.remove(0));
    }

    /** Убирает сыгранную карту из руки в сброс и возвращает её. */
    Card spend(boolean white, int cardId) {
        var hand = hand(white);
        Card card = find(white, cardId);
        hand.remove(card);
        discard.add(card);
        return card;
    }

    Card find(boolean white, int cardId) {
        for (Card card : hand(white)) if (card.id() == cardId) return card;
        throw new IllegalArgumentException("Карты нет в руке");
    }

    /** Разведке нужны две верхние карты: при нехватке под колоду ложится перемешанный сброс. */
    void prepareScout() {
        if (deck.size() < 2 && !discard.isEmpty()) {
            Collections.shuffle(discard);
            deck.addAll(discard);
            discard.clear();
        }
    }

    /** Заменяет неизвестные этому игроку карты (рука соперника, колода, сброс) картами UNKNOWN. */
    void hideFrom(boolean viewer) {
        mask(hand(!viewer), -100);
        mask(deck, -200);
        mask(discard, -300);
    }

    private static void mask(List<Card> cards, int firstId) {
        for (int i = 0; i < cards.size(); i++) cards.set(i, CardCatalog.create(firstId - i, CardType.UNKNOWN));
    }

    CardPiles copy() {
        return new CardPiles(new ArrayList<>(whiteHand), new ArrayList<>(blackHand), new ArrayList<>(deck),
                new ArrayList<>(discard), whiteLimit, blackLimit);
    }
}
