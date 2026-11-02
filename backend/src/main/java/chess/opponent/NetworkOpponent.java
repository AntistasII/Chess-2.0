package chess.opponent;

import chess.game.GameMode;

/** Сетевой игрок присылает ход через API. Авторизацию и очередь проверяют сервисы. */
public final class NetworkOpponent extends Opponent {
    @Override public GameMode mode() { return GameMode.NETWORK; }
    @Override public boolean network() { return true; }
}
