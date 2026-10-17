package chess.opponent;

/** Сетевой игрок присылает ход через API. Авторизацию и очередь проверяют сервисы. */
public final class NetworkOpponent extends Opponent {
    @Override public String mode() { return "network"; }
    @Override public boolean network() { return true; }
}
