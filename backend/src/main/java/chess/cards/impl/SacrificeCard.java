package chess.cards.impl;

import chess.cards.*;
import chess.game.Action;
import chess.pieces.Piece;
import java.util.ArrayList;
import java.util.List;

@CardDefinition(type = CardType.SACRIFICE, copies = 2)
public final class SacrificeCard extends Card {
    public SacrificeCard(int id) { super(id, CardType.SACRIFICE, "Принести в жертву"); }
    @Override public List<Action> actions(CardContext context) {
        var actions = new ArrayList<Action>();
        for (int square = 0; square < context.board().size(); square++) {
            Piece p = context.board().get(square);
            if (p == null || p.white() != context.whiteTurn() || p.astral() || p.type() == 'P' || p.type() == 'K') continue;
            for (int start : context.startingSquares('P'))
                if ((start == square || context.board().get(start) == null) && !context.blocked(start))
                    actions.add(Action.card(this, square, start, ""));
        }
        return actions;
    }
    @Override public void apply(CardContext context, Action action) {
        Piece p = context.board().get(action.to());
        context.disableCastle(p, action.to());
        context.board().set(action.to(), null);
        // Жертва удаляется из партии, а не попадает в список доступных для возрождения.
        context.board().set(action.other(), context.newPiece('P'));
    }
}
