package chess.server;

import chess.api.HttpJson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

/**
 * Раздаёт собранный интерфейс; запрещает выход за пределы корневой папки.
 */
public final class StaticFileHandler implements HttpHandler {
    private final Path root;

    public StaticFileHandler(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /**
     * Раздаёт собранный интерфейс, проверяя, что путь остаётся внутри frontend/dist.
     */
    @Override
    public void handle(HttpExchange x) throws IOException {
        String raw = x.getRequestURI().getPath();
        Path file = root.resolve(raw.equals("/") ? "index.html" : raw.substring(1)).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            HttpJson.send(x, 404, Map.of("error", "Сначала соберите интерфейс: scripts/build.sh"));
            return;
        }
        String name = file.toString();
        String mime = name.endsWith(".html") ? "text/html; charset=utf-8" : name.endsWith(".js") ? "text/javascript; charset=utf-8" : name.endsWith(".css") ? "text/css; charset=utf-8" : "application/octet-stream";
        x.getResponseHeaders().set("Content-Type", mime);
        x.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        x.sendResponseHeaders(200, Files.size(file));
        try (var stream = x.getResponseBody()) {
            Files.copy(file, stream);
        }
    }
}
