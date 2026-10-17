package chess.game;

import chess.cards.Card;

/**
 * Описание действия для правил, ИИ и интерфейса; id однозначно задаёт выбранный вариант.
 */
public record Action(String id, String kind, int from, int to, int other, int cardId, String option) {
    /**
     * Шахматный ход с выбранным превращением или признаком рокировки.
     */
    public static Action move(int from, int to, String promotion) {
        return new Action("move:" + from + ":" + to + ":" + promotion, "move", from, to, -1, -1, promotion);
    }

    /**
     * Заклинание с целью, второй фигурой или дополнительным выбором.
     */
    public static Action card(Card c, int to, int other, String option) {
        return new Action("card:" + c.id() + ":" + to + ":" + other + ":" + option, "card", -1, to, other, c.id(), option);
    }

    /**
     * Добор из общей колоды расходует одно действие.
     */
    static Action draw() {
        return new Action("draw", "draw", -1, -1, -1, -1, "");
    }

}
