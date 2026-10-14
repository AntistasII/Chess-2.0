package chess.server;

import chess.ai.Ai;
import chess.ai.ChatGptOpponent;
import chess.api.ApiHandler;
import chess.api.HttpJson;
import chess.game.GameSession;
import com.sun.net.httpserver.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Точка запуска локального сервера и раздача собранного интерфейса. */
public final class Server {
    /** Запускает сервер на локальном адресе; отдельные запросы обслуживаются виртуальными потоками. */
    public static void main(String[] args) throws IOException {
        String cloudPort = System.getenv("PORT");
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Integer.parseInt(cloudPort == null ? "8080" : cloudPort);
        String hostKey = System.getenv().getOrDefault("CHESS_HOST_KEY", "");
        String guestKey = System.getenv().getOrDefault("CHESS_GUEST_KEY", "");
        if (cloudPort != null || !hostKey.isBlank()) {
            if (hostKey.length() < 16 || guestKey.length() < 16 || hostKey.equals(guestKey))
                throw new IOException("Задайте разные CHESS_HOST_KEY и CHESS_GUEST_KEY длиной от 16 символов.");
        }
        var config = chess.config.ApplicationConfig.load(Path.of("application.yml"));
        var chatGpt = ChatGptOpponent.fromConfig(config);
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        var session = new GameSession(Ai::choose, chatGpt);
        if (!hostKey.isBlank()) session.newGame("network", 3, "normal");
        server.createContext("/api/", new ApiHandler(session, hostKey, guestKey));
        server.createContext("/health", exchange -> HttpJson.send(exchange, 200, Map.of("status", "ok")));
        server.createContext("/", Server::staticFile);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("Шахматы с картами: http://127.0.0.1:" + port);
        for (var interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements();) {
            var net = interfaces.nextElement();
            if (!net.isUp() || net.isLoopback()) continue;
            for (var addresses = net.getInetAddresses(); addresses.hasMoreElements();) {
                var address = addresses.nextElement();
                if (address instanceof Inet4Address) System.out.println("Для второго игрока: http://" + address.getHostAddress() + ":" + port);
            }
        }
    }

    /** Раздаёт собранный интерфейс, проверяя, что путь остаётся внутри frontend/dist. */
    private static void staticFile(HttpExchange x) throws IOException {
        Path root = Path.of("frontend/dist").toAbsolutePath().normalize();
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
