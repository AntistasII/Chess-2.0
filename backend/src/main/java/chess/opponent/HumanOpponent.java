package chess.opponent;

import chess.game.GameMode;

/** Второй человек за тем же компьютером: оба игрока действуют вручную. */
public final class HumanOpponent extends Opponent {
    @Override public GameMode mode() { return GameMode.HUMAN; }
}
