package chess.model;

/**
 * Инструкция контроллеру выдать или удалить cookie. Нулевой срок означает удаление.
 */
public record CookieChange(String name, String value, int maxAge, boolean secure) {
}
