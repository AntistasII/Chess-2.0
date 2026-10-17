package chess.model;

/**
 * Данные соединения, нужные для определения игрока. Не содержит HTTP-объектов.
 */
public record ClientIdentity(boolean loopback, String cookie) {
}
