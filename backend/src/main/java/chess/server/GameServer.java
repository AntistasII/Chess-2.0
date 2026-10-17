package chess.server;

import chess.api.HttpJson;
import chess.controller.GameController;
import chess.service.GameService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Регистрирует маршруты и управляет жизненным циклом HTTP-сервера.
 */
public final class GameServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public GameServer(int port, GameService service) throws IOException {
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/api/", new GameController(service));
        server.createContext("/health", exchange -> HttpJson.send(exchange, 200, Map.of("status", "ok")));
        server.createContext("/", new StaticFileHandler(Path.of("frontend/dist")));
        server.setExecutor(executor);
    }

    public void start() throws IOException {
        server.start();
        int port = server.getAddress().getPort();
        System.out.println("Шахматы с картами: http://127.0.0.1:" + port);
        for (var interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements(); ) {
            var net = interfaces.nextElement();
            if (!net.isUp() || net.isLoopback()) continue;
            for (var addresses = net.getInetAddresses(); addresses.hasMoreElements(); ) {
                var address = addresses.nextElement();
                if (address instanceof Inet4Address)
                    System.out.println("Для второго игрока: http://" + address.getHostAddress() + ":" + port);
            }
        }
    }

    @Override
    public void close() {
        server.stop(1);
        executor.shutdownNow();
    }
}

