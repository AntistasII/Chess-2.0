package chess.cards.impl;

import chess.cards.*;
import chess.game.Action;
import java.util.List;

@CardDefinition(type = CardType.UNDO_MOVE, copies = 2, durationTurns = 1)
public final class UndoMoveCard extends Card {
    public UndoMoveCard(int id) { super(id, CardType.UNDO_MOVE, "Отмена хода"); }
    @Override public List<Action> actions(CardContext context) {
        int target = context.undoTarget();
        return target < 0 ? List.of() : List.of(Action.card(this, target, -1, ""));
    }
    @Override public void apply(CardContext context, Action action) { context.undoLastMove(effectEndsAt(context.ply())); }
}
