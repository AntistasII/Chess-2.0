package chess.cards;

import chess.game.Action;
import chess.pieces.PieceType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Общая механика возвращения взятой фигуры на свободную стартовую клетку.
 */
public abstract class ReviveCard extends Card {
    protected ReviveCard(int id, CardType type, String name) {
        super(id, type, name);
    }

    protected abstract boolean accepts(PieceType type);

    @Override
    public List<Action> actions(CardContext context) {
        var types = EnumSet.noneOf(PieceType.class);
        for (var piece : context.captured())
            if (piece.white() == context.whiteTurn() && accepts(piece.type())) types.add(piece.type());
        var actions = new ArrayList<Action>();
        for (PieceType type : types)
            for (int square : context.startingSquares(type))
                if (context.board().get(square) == null && !context.blocked(square)) actions.add(Action.card(this, square, -1, String.valueOf(type.code())));
        return actions;
    }

    @Override
    public void apply(CardContext context, Action action) {
        PieceType type = PieceType.fromCode(action.option());
        var old = context.captured().stream().filter(p -> p.white() == context.whiteTurn() && p.type() == type)
                .findFirst().orElseThrow();
        context.captured().remove(old);
        var revived = context.newPiece(type);
        if (type != PieceType.PAWN) revived.markMoved();
        context.board().set(action.to(), revived);
    }
}
