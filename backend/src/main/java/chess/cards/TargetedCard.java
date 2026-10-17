package chess.cards;

import chess.game.Action;
import chess.pieces.Piece;

import java.util.ArrayList;
import java.util.List;

/**
 * Общие ограничения: астрал недоступен, чужой щит защищает от карт.
 */
public abstract class TargetedCard extends Card implements PieceTargetedCard {
    protected TargetedCard(int id, CardType type, String name) {
        super(id, type, name);
    }

    protected final boolean selectable(CardContext context, Piece piece) {
        return piece != null && !piece.astral()
                && (piece.white() == context.whiteTurn() || !piece.shield())
                && canTarget(piece, piece.white() == context.whiteTurn());
    }

    @Override
    public List<Action> actions(CardContext context) {
        var actions = new ArrayList<Action>();
        for (int square = 0; square < context.board().size(); square++)
            if (selectable(context, context.board().get(square)))
                actions.add(Action.card(this, square, -1, ""));
        return actions;
    }

    @Override
    public final void apply(CardContext context, Action action) {
        applyTo(context, action);
    }
}
