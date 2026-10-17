package chess.cards.impl;

import chess.cards.*;
import chess.game.SquareEffect;

@CardDefinition(type = CardType.WHITE_HOLE, copies = 2, durationTurns = 3, freezeTurns = 0)
public final class WhiteHoleCard extends SquareEffectCard {
    public WhiteHoleCard(int id) {
        super(id, CardType.WHITE_HOLE, "Белая дыра", SquareEffect.Type.WHITE_HOLE);
    }
}
