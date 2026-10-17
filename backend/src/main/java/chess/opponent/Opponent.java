package chess.opponent;

import chess.game.Game;
import chess.game.MoveSelector;

/** Способ участия соперника в партии: автоматический выбор или действия человека. */
public abstract class Opponent implements MoveSelector {
    public abstract String mode();

    public boolean automatic() { return false; }
    public boolean network() { return false; }
    public boolean requiresScoutPreview() { return false; }

    /** У человека ход поступает командой play, поэтому автоматический выбор недоступен. */
    @Override public String choose(Game game) {
        throw new IllegalArgumentException("Ожидается ход игрока");
    }
}
