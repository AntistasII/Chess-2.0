package chess.model;

import java.util.List;

/**
 * Данные ответа сервиса. Сериализация и HTTP-заголовки относятся к контроллеру.
 */
public record ApiResponse(Object body, List<CookieChange> cookies, boolean download) {
    public ApiResponse(Object body, List<CookieChange> cookies) {
        this(body, cookies, false);
    }
}
