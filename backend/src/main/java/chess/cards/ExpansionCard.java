package chess.cards;

import chess.game.Action;
import java.util.List;

/** Одна новая линия с выбранной стороны, без перемещения фигур относительно старых клеток. */
public abstract class ExpansionCard extends Card {
    private final int left, right, bottom, top;
    protected ExpansionCard(int id, CardType type, String name, int left, int right, int bottom, int top) {
        super(id, type, name);
        this.left = left; this.right = right; this.bottom = bottom; this.top = top;
    }
    @Override public List<Action> actions(CardContext context) {
        var board = context.board();
        return board.width() + left + right > 128 || board.height() + bottom + top > 128
                ? List.of() : List.of(Action.card(this, -1, -1, ""));
    }
    @Override public void apply(CardContext context, Action action) { context.expandBoard(left, right, bottom, top); }
}
