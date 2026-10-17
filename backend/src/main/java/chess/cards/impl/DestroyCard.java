package chess.cards.impl;

import chess.cards.*;
import chess.game.Action;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@CardDefinition(type = CardType.DESTROY_CARD, copies = 2)
public final class DestroyCard extends Card {
    public DestroyCard(int id) {
        super(id, CardType.DESTROY_CARD, "Уничтожить карту");
    }

    @Override
    public List<Action> actions(CardContext context) {
        return context.hand(!context.whiteTurn()).isEmpty() ? List.of() : List.of(Action.card(this, -1, -1, ""));
    }

    @Override
    public void apply(CardContext context, Action action) {
        var enemy = context.hand(!context.whiteTurn());
        enemy.remove(ThreadLocalRandom.current().nextInt(enemy.size()));
    }
}
