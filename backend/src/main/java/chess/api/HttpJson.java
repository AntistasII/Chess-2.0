package chess.api;

import chess.serialization.JsonCodec;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Общая запись JSON-ответов и HTTP-заголовков.
 */
public final class HttpJson {
    private HttpJson() {
    }

    /**
     * Записывает JSON в UTF-8 и запрещает кеширование состояния партии.
     */
    public static void send(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] data = JsonCodec.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, data.length);
        try (var stream = exchange.getResponseBody()) {
            stream.write(data);
        }
    }

    /** Все ошибки API имеют одинаковую структуру, которую понимает frontend. */
    public static void error(HttpExchange exchange, int status, String message) throws IOException {
        send(exchange, status, Map.of("error", message));
    }
}
