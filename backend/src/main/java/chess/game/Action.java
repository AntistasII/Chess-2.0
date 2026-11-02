package chess.game;

import chess.cards.Card;
import chess.pieces.PieceType;

import java.util.Optional;

/**
 * Описание действия для правил, ИИ и интерфейса; id однозначно задаёт выбранный вариант
 * и служит протоколом с клиентом («move:12:28:», «card:5:20:-1:», «draw»).
 */
public record Action(String id, ActionKind kind, int from, int to, int other, int cardId, String option) {
    /** Идентификатор добора из колоды. */
    public static final String DRAW_ID = "draw";
    /** Вариант хода, означающий рокировку. */
    public static final String CASTLE = "castle";

    /**
     * Шахматный ход с выбранным превращением или признаком рокировки.
     */
    public static Action move(int from, int to, String promotion) {
        return new Action("move:" + from + ":" + to + ":" + promotion, ActionKind.MOVE, from, to, -1, -1, promotion);
    }

    /**
     * Заклинание с целью, второй фигурой или дополнительным выбором.
     */
    public static Action card(Card c, int to, int other, String option) {
        return new Action("card:" + c.id() + ":" + to + ":" + other + ":" + option, ActionKind.CARD, -1, to, other, c.id(), option);
    }

    /**
     * Добор из общей колоды расходует одно действие.
     */
    static Action draw() {
        return new Action(DRAW_ID, ActionKind.DRAW, -1, -1, -1, -1, "");
    }

    public boolean isMove() { return kind == ActionKind.MOVE; }

    public boolean isCard() { return kind == ActionKind.CARD; }

    public boolean isDraw() { return kind == ActionKind.DRAW; }

    public boolean isCastling() { return isMove() && CASTLE.equals(option); }

    /** Фигура, в которую превращается пешка этим ходом. */
    public Optional<PieceType> promotion() {
        return isMove() && option.length() == 1 ? Optional.of(PieceType.fromCode(option)) : Optional.empty();
    }
}
