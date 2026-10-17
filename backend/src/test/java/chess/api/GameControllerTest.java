package chess.api;

import chess.controller.GameController;

import chess.opponent.ChatGptOpponent;
import chess.game.GameSession;
import chess.api.NetworkApiTest.Exchange;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Проверяем внешний HTTP-контракт, а не внутреннее разбиение обработчика на классы. */
class GameControllerTest {
    private static Exchange post(String path, Map<String, Object> body) {
        return new Exchange(path, true, body, null);
    }

    private static Exchange handle(GameController api, Exchange exchange) throws Exception {
        api.handle(exchange); return exchange;
    }

    @Test void rejectsInvalidTransportBeforeChangingGame() throws Exception {
        var session = chess.game.TestSessions.create(g -> "draw");
        var api = new GameController(session);
        var original = session.state();
        var method = post("/api/new", Map.of()); method.method = "DELETE";
        assertEquals(405, handle(api, method).status);
        var content = post("/api/new", Map.of()); content.request.remove("Content-Type");
        assertEquals(415, handle(api, content).status);
        var origin = post("/api/new", Map.of()); origin.request.set("Origin", "https://unrelated.example");
        assertEquals(403, handle(api, origin).status);
        var json = post("/api/new", Map.of());
        json.input = new ByteArrayInputStream("{".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, handle(api, json).status);
        assertEquals(original, session.state());
    }

    @Test void keepsSeparateLimitsForCommandsAndSaveFiles() throws Exception {
        var session = chess.game.TestSessions.create(g -> "draw");
        var api = new GameController(session);
        var command = post("/api/new", Map.of());
        command.input = new ByteArrayInputStream(new byte[16_385]);
        assertEquals(413, handle(api, command).status);
        var snapshot = new LinkedHashMap<>(session.exportGame(0));
        snapshot.put("padding", "x".repeat(20_000));
        assertEquals(200, handle(api, post("/api/import", Map.of("version", 0, "save", snapshot))).status);
        var oversized = post("/api/import", Map.of());
        oversized.input = new ByteArrayInputStream(new byte[10_000_001]);
        assertEquals(413, handle(api, oversized).status);
    }

    @Test void checksVersionBeforeParsingCommandArguments() throws Exception {
        var api = new GameController(chess.game.TestSessions.create(g -> "draw"));
        assertEquals(409, handle(api, post("/api/scout", Map.of("version", -1, "cardId", "bad"))).status);
        assertEquals(400, handle(api, post("/api/scout", Map.of("version", 0, "cardId", "bad"))).status);
        assertEquals(404, handle(api, post("/api/missing", Map.of())).status);
        assertEquals(404, handle(api, post("/api/state", Map.of())).status);
    }

    @Test void keepsAiFailureStatusForFrontendRetries() throws Exception {
        var session = chess.game.TestSessions.create(g -> { throw new ChatGptOpponent.Failure("test-unavailable"); });
        session.play(0, "draw");
        var response = handle(new GameController(session), post("/api/ai", Map.of("version", 1)));
        assertEquals(502, response.status);
        assertEquals("test-unavailable", response.json().get("error"));
    }

    @Test void invalidOpenAiConfigurationDoesNotTriggerFrontendRetries() throws Exception {
        var session = chess.game.TestSessions.create(g -> {
            throw new ChatGptOpponent.Failure("OpenAI: API-ключ недействителен (HTTP 401)", false);
        });
        session.play(0, "draw");
        var response = handle(new GameController(session), post("/api/ai", Map.of("version", 1)));
        assertEquals(424, response.status);
        assertTrue(response.json().get("error").toString().contains("API-ключ"));
        assertEquals(1L, session.state().get("version"));
    }

    @Test void slowAiDoesNotBlockStateOrResetAndCannotCommitAfterReset() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var session = chess.game.TestSessions.create(g -> {
            started.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            return "draw";
        });
        session.play(0, "draw");
        var api = new GameController(session);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ai = executor.submit(() -> handle(api, post("/api/ai", Map.of("version", 1))));
            try {
                assertTrue(started.await(3, TimeUnit.SECONDS));
                var read = executor.submit(() -> handle(api, new Exchange("/api/state", true, null, null)));
                assertEquals(200, read.get(3, TimeUnit.SECONDS).status);
                var reset = executor.submit(() -> handle(api, post("/api/reset", Map.of("version", 1))));
                assertEquals(200, reset.get(3, TimeUnit.SECONDS).status);
            } finally { release.countDown(); }
            assertEquals(409, ai.get(3, TimeUnit.SECONDS).status);
        }
        assertEquals(0, ((Number)session.state().get("ply")).intValue());
    }
}
