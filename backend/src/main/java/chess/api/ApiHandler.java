package chess.api;

import chess.game.GameSession;
import chess.json.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/** Проверяет HTTP-запросы и передаёт команды сессии, которая контролирует очередь и версию партии. */
public final class ApiHandler implements HttpHandler {
    private static final int MAX_BODY_BYTES = 16_384;
    private static final Set<String> COMMAND_PATHS = Set.of("/api/action", "/api/ai", "/api/scout", "/api/draw", "/api/save", "/api/load", "/api/export", "/api/import");
    private final GameSession session;

    public ApiHandler(GameSession session) {
        this.session = session;
    }

    /** Ограничивает размер тела запроса и переводит ошибки правил в HTTP-ответы. */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!validate(exchange)) return;
            int limit = exchange.getRequestURI().getPath().equals("/api/import") ? 10_000_000 : MAX_BODY_BYTES;
            byte[] body = exchange.getRequestBody().readNBytes(limit + 1);
            if (body.length > limit) {
                error(exchange, 413, "Слишком большой запрос");
                return;
            }
            Map<String, Object> input = body.length == 0 ? Map.of() : Json.object(new String(body, StandardCharsets.UTF_8));
            route(exchange, input);
        } catch (chess.ai.ChatGptOpponent.Failure e) {
            error(exchange, 502, e.getMessage());
        } catch (GameSession.StalePositionException e) {
            error(exchange, 409, e.getMessage());
        } catch (IllegalArgumentException | ClassCastException | NullPointerException e) {
            error(exchange, 400, e.getMessage() == null ? "Некорректный запрос" : e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            error(exchange, 500, "Ошибка сервера");
        }
    }

    /** Проверяет метод, источник команды и тип содержимого до чтения JSON. */
    private boolean validate(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!Set.of("GET", "POST").contains(method)) {
            error(exchange, 405, "Метод недоступен");
            return false;
        }
        if (method.equals("POST")) {
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            int port = exchange.getLocalAddress().getPort();
            if (origin != null && !Set.of("http://127.0.0.1:" + port, "http://localhost:" + port,
                    "http://127.0.0.1:5173", "http://localhost:5173").contains(origin)) {
                error(exchange, 403, "Недопустимый источник запроса");
                return false;
            }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.startsWith("application/json")) {
                error(exchange, 415, "Требуется JSON");
                return false;
            }
        }
        return true;
    }

    /** Выбирает команду сессии; проверка версии имеет приоритет перед чтением аргументов. */
    private void route(HttpExchange exchange, Map<String, Object> input) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/api/state") && exchange.getRequestMethod().equals("GET")) {
            HttpJson.send(exchange, 200, session.state());
            return;
        }
        if (!exchange.getRequestMethod().equals("POST")) {
            error(exchange, 404, "Неизвестный запрос");
            return;
        }
        if (path.equals("/api/new")) {
            HttpJson.send(exchange, 200, session.newGame((String) input.getOrDefault("mode", "ai"),
                    ((Number) input.getOrDefault("handLimit", 3)).intValue(),
                    (String) input.getOrDefault("difficulty", "normal")));
            return;
        }
        if (!COMMAND_PATHS.contains(path)) {
            error(exchange, 404, "Неизвестный запрос");
            return;
        }
        long version = input.get("version") instanceof Number number ? number.longValue() : -1;
        session.requireVersion(version);
        if (path.equals("/api/export")) {
            var snapshot = session.exportGame(version);
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=chess-game.json");
            HttpJson.send(exchange, 200, snapshot);
            return;
        }
        Object state = switch (path) {
            case "/api/import" -> session.importGame(version, asSave(input.get("save")));
            case "/api/save" -> session.save(version);
            case "/api/load" -> session.load(version);
            case "/api/ai" -> session.runAi(version);
            case "/api/scout" -> session.scout(version, ((Number) input.get("cardId")).intValue());
            case "/api/draw" -> session.claimDraw(version);
            default -> session.play(version, (String) input.get("actionId"));
        };
        HttpJson.send(exchange, 200, state);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asSave(Object value) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Некорректный файл сохранения.");
        return (Map<String, Object>) value;
    }

    /** Единый формат сообщения об ошибке для клиента. */
    private static void error(HttpExchange exchange, int status, String message) throws IOException {
        HttpJson.send(exchange, status, Map.of("error", message));
    }
}
