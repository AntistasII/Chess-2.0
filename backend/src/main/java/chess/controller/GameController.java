package chess.controller;

import chess.opponent.ChatGptOpponent;
import chess.api.ApiException;
import chess.api.HttpJson;
import chess.api.HttpRequestReader;
import chess.game.GameSession;
import chess.model.ClientIdentity;
import chess.model.CookieChange;
import chess.service.GameService;
import chess.service.ServiceException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * HTTP-контроллер: читает запрос, вызывает сервис и переводит результат в JSON/заголовки.
 */
public final class GameController implements HttpHandler {
    private final GameService service;

    public GameController(GameService service) {
        this.service = service;
    }

    public GameController(GameSession session) {
        this(session, "", "");
    }

    public GameController(GameSession session, String hostKey, String guestKey) {
        this(new GameService(session, hostKey, guestKey));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            var request = HttpRequestReader.read(exchange);
            var client = new ClientIdentity(exchange.getRemoteAddress().getAddress().isLoopbackAddress(),
                    exchange.getRequestHeaders().getFirst("Cookie"));
            var response = service.execute(client, request);
            for (CookieChange cookie : response.cookies()) writeCookie(exchange, cookie);
            if (response.download())
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=chess-game.json");
            HttpJson.send(exchange, 200, response.body());
        } catch (ApiException e) {
            HttpJson.error(exchange, e.status(), e.getMessage());
        } catch (ServiceException e) {
            int status = switch (e.reason()) {
                case UNAUTHORIZED -> 401;
                case FORBIDDEN -> 403;
                case NOT_FOUND -> 404;
            };
            HttpJson.error(exchange, status, e.getMessage());
        } catch (ChatGptOpponent.Failure e) {
            // Ошибка ключа или модели требует исправления настроек, а не повторных запросов.
            HttpJson.error(exchange, e.retryable() ? 502 : 424, e.getMessage());
        } catch (GameSession.StalePositionException e) {
            HttpJson.error(exchange, 409, e.getMessage());
        } catch (IllegalArgumentException | ClassCastException | NullPointerException e) {
            HttpJson.error(exchange, 400, e.getMessage() == null ? "Некорректный запрос" : e.getMessage());
        } catch (Exception e) {
            // Внутренние подробности остаются в журнале, клиент получает общий текст.
            e.printStackTrace();
            HttpJson.error(exchange, 500, "Ошибка сервера");
        }
    }

    private static void writeCookie(HttpExchange exchange, CookieChange cookie) {
        exchange.getResponseHeaders().add("Set-Cookie", cookie.name() + "=" + cookie.value()
                + "; Path=/; Max-Age=" + cookie.maxAge() + "; HttpOnly; SameSite=Strict"
                + (cookie.secure() ? "; Secure" : ""));
    }
}
