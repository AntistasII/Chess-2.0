package chess.api;

import chess.model.ApiRequest;
import chess.model.GameOperation;
import chess.serialization.JsonCodec;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Только HTTP: заголовки, ограничение тела и декодирование JSON.
 */
public final class HttpRequestReader {
    private static final int MAX_BODY_BYTES = 16_384;
    private static final int MAX_SAVE_BYTES = 10_000_000;

    private HttpRequestReader() {}

    public static ApiRequest read(HttpExchange exchange) throws IOException {
        validateHeaders(exchange);
        GameOperation operation = operationFor(exchange.getRequestURI().getPath());
        return new ApiRequest(operation, exchange.getRequestMethod().equals("POST"), readBody(exchange, operation));
    }

    private static Map<String, Object> readBody(HttpExchange exchange, GameOperation operation) throws IOException {
        // Файл партии может быть значительно больше обычной команды.
        int limit = operation == GameOperation.IMPORT ? MAX_SAVE_BYTES : MAX_BODY_BYTES;
        byte[] body = exchange.getRequestBody().readNBytes(limit + 1);
        if (body.length > limit) throw new ApiException(413, "Слишком большой запрос");
        if (body.length == 0) return Map.of();
        return JsonCodec.readObject(new String(body, StandardCharsets.UTF_8));
    }

    private static GameOperation operationFor(String path) {
        return switch (path) {
            case "/api/state" -> GameOperation.STATE;
            case "/api/login" -> GameOperation.LOGIN;
            case "/api/new" -> GameOperation.NEW_GAME;
            case "/api/reset" -> GameOperation.RESET;
            case "/api/action" -> GameOperation.PLAY;
            case "/api/ai" -> GameOperation.AI;
            case "/api/scout" -> GameOperation.SCOUT;
            case "/api/draw" -> GameOperation.DRAW;
            case "/api/resign" -> GameOperation.RESIGN;
            case "/api/save" -> GameOperation.SAVE;
            case "/api/load" -> GameOperation.LOAD;
            case "/api/export" -> GameOperation.EXPORT;
            case "/api/import" -> GameOperation.IMPORT;
            default -> GameOperation.UNKNOWN;
        };
    }

    /**
     * Отсекаем неподходящие запросы до чтения тела и любых изменений партии.
     */
    private static void validateHeaders(HttpExchange exchange) {
        String method = exchange.getRequestMethod();
        if (!Set.of("GET", "POST").contains(method)) throw new ApiException(405, "Метод недоступен");
        if (!method.equals("POST")) return;

        if (!allowedOrigin(exchange)) throw new ApiException(403, "Недопустимый источник запроса");
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json"))
            throw new ApiException(415, "Требуется JSON");
    }

    private static boolean allowedOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        // У CLI-клиентов может не быть Origin. Право игрока всё равно проверяется отдельно.
        if (origin == null) return true;
        String host = exchange.getRequestHeaders().getFirst("Host");
        int port = exchange.getLocalAddress().getPort();
        // HTTPS нужен для Railway, localhost:5173 — для Vite при разработке.
        return origin.equals("http://" + host) || origin.equals("https://" + host)
                || Set.of("http://127.0.0.1:" + port, "http://localhost:" + port,
                "http://127.0.0.1:5173", "http://localhost:5173").contains(origin);
    }
}
