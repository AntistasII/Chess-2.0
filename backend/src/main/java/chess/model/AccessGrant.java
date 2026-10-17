package chess.model;

import java.util.List;

/**
 * Результат определения роли и cookies для нового локального игрока.
 */
public record AccessGrant(boolean owner, List<CookieChange> cookies) {
}
