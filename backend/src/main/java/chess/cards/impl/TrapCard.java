package chess.cards.impl;

import chess.cards.*;
import chess.game.SquareEffect;

@CardDefinition(type = CardType.TRAP, copies = 2, durationTurns = 3, freezeTurns = 3)
public final class TrapCard extends SquareEffectCard {
    public TrapCard(int id) { super(id, CardType.TRAP, "Капкан", SquareEffect.Type.TRAP); }
}
