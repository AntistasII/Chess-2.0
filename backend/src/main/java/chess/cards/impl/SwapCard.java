package chess.cards.impl;

import chess.cards.CardType;
import chess.cards.CardDefinition;
import chess.cards.CardContext;
import chess.cards.TargetedCard;

import chess.game.Action;
import chess.pieces.Piece;

import java.util.ArrayList;
import java.util.List;

@CardDefinition(type = CardType.SWAP, copies = 2)
public final class SwapCard extends TargetedCard {
    public SwapCard(int id) {
        super(id, CardType.SWAP, "Перестановка");
    }

    @Override
    public boolean canTarget(Piece piece, boolean own) {
        return own && piece.type() != 'K';
    }

    @Override
    public List<Action> actions(CardContext context) {
        var actions = new ArrayList<Action>();
        var board = context.board();
        var geometry = board.geometry();
        for (int first = 0; first < board.size(); first++) {
            Piece p = board.get(first);
            if (!selectable(context, p)) continue;
            for (int second = first + 1; second < board.size(); second++) {
                Piece q = board.get(second);
                if (!selectable(context, q)) continue;
                if (Math.max(Math.abs(geometry.rank(second) - geometry.rank(first)),
                        Math.abs(geometry.file(second) - geometry.file(first))) != 1) continue;
                if (p.type() == 'P' && edge(geometry.rank(second), board.height())) continue;
                if (q.type() == 'P' && edge(geometry.rank(first), board.height())) continue;
                actions.add(Action.card(this, first, second, ""));
            }
        }
        return actions;
    }

    private static boolean edge(int rank, int height) {
        return rank == 0 || rank == height - 1;
    }

    @Override
    public void applyTo(CardContext context, Action action) {
        var board = context.board();
        Piece p = board.get(action.to()), q = board.get(action.other());
        context.disableCastle(p, action.to());
        context.disableCastle(q, action.other());
        board.set(action.to(), q);
        board.set(action.other(), p);
        p.consumeMove();
        q.consumeMove();
    }
}
