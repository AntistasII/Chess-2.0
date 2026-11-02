package chess.opponent;

import chess.game.Game;
import chess.game.GameMode;
import chess.game.MoveSelector;

/**
 * Способ участия соперника (чёрных) в партии: автоматический выбор хода
 * или действия человека, которые приходят командами.
 */
public abstract class Opponent implements MoveSelector {
    /** Режим партии, в котором участвует этот соперник. */
    public abstract GameMode mode();

    /** Ход выбирается программой; сессия запрашивает его отдельной командой. */
    public boolean automatic() { return false; }

    /** Соперник — удалённый игрок со своим браузером. */
    public boolean network() { return false; }

    /**
     * Разведка выполняется в два запроса: сначала карта расходуется и открывает две
     * верхние карты, затем соперник выбирает их порядок.
     */
    public boolean requiresScoutPreview() { return false; }

    /** У человека ход поступает командой play, поэтому автоматический выбор недоступен. */
    @Override public String choose(Game game) {
        throw new IllegalArgumentException("Ожидается ход игрока");
    }
}
