package chess.cards;

import chess.game.Action;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Общая механика возвращения взятой фигуры на свободную стартовую клетку.
 */
public abstract class ReviveCard extends Card {
    protected ReviveCard(int id, CardType type, String name) {
        super(id, type, name);
    }

    protected abstract boolean accepts(char type);

    @Override
    public List<Action> actions(CardContext context) {
        var types = new HashSet<Character>();
        for (var piece : context.captured())
            if (piece.white() == context.whiteTurn() && accepts(piece.type())) types.add(piece.type());
        var actions = new ArrayList<Action>();
        for (char type : types)
            for (int square : context.startingSquares(type))
                if (context.board().get(square) == null && !context.blocked(square)) actions.add(Action.card(this, square, -1, "" + type));
        return actions;
    }

    @Override
    public void apply(CardContext context, Action action) {
        char type = action.option().charAt(0);
        var old = context.captured().stream().filter(p -> p.white() == context.whiteTurn() && p.type() == type)
                .findFirst().orElseThrow();
        context.captured().remove(old);
        var revived = context.newPiece(type);
        if (type != 'P') revived.markMoved();
        context.board().set(action.to(), revived);
    }
}
