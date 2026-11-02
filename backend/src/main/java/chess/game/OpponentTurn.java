package chess.game;

import chess.cards.CardType;
import chess.opponent.Opponent;

/**
 * Ход автоматического соперника в два этапа: расчёт на копии без блокировки партии
 * и проверенное применение результата к живой партии.
 */
final class OpponentTurn {
    private OpponentTurn() {}

    /**
     * Позиция, которую видит соперник: чужая рука, колода и невидимые капканы скрыты.
     * Начатая разведка раскрывает ему только две верхние карты.
     */
    static Game observation(Game live) {
        Game snapshot = live.copy();
        snapshot.setHistory(live.history());
        Game observation = snapshot.observationForCurrentPlayer();
        if (snapshot.scoutingActive()) {
            var deck = observation.cardPiles().deck();
            deck.set(0, snapshot.cardPiles().deck().get(0));
            deck.set(1, snapshot.cardPiles().deck().get(1));
        }
        return observation;
    }

    /**
     * Применяет выбранное действие, если оно допустимо в текущей позиции.
     * Соперник с предпросмотром разведки сначала только расходует карту: порядок
     * двух открытых карт он выберет следующим запросом.
     */
    static void apply(Game live, Opponent opponent, String actionId) {
        Action action = live.legalActions().stream().filter(a -> a.id().equals(actionId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Соперник вернул недопустимый ход. Повторите запрос."));
        if (opponent.requiresScoutPreview() && !live.scoutingActive() && action.isCard()
                && live.cardInHand(action.cardId()).type() == CardType.SCOUT) live.startScout(action.cardId());
        else live.play(actionId);
    }
}
