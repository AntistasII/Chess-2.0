package chess.cards.impl;

import chess.cards.*;
import chess.game.SquareEffect;

@CardDefinition(type = CardType.HIDDEN_TRAP, copies = 2, durationTurns = 3, freezeTurns = 3)
public final class HiddenTrapCard extends SquareEffectCard {
    public HiddenTrapCard(int id) { super(id, CardType.HIDDEN_TRAP, "Невидимый капкан", SquareEffect.Type.HIDDEN_TRAP); }
}
