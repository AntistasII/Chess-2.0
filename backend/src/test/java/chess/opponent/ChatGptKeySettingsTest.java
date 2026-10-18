package chess.opponent;

import chess.game.TestSessions;
import chess.serialization.JsonCodec;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ChatGptKeySettingsTest {
    @Test void enteredKeyReachesTransportButNeverStateOrSave() {
        var usedKey = new AtomicReference<String>();
        var original = new ChatGptOpponent("", "test-model", "test", (key, body) -> {
            usedKey.set(key);
            assertFalse(body.contains("entered-test-key"));
            var request = JsonCodec.readObject(body);
            var input = JsonCodec.readObject((String) request.get("input"));
            var candidates = (List<?>) input.get("candidates");
            String id = (String) ((List<?>) candidates.getFirst()).getFirst();
            String text = JsonCodec.toJson(Map.of("actionId", id));
            return new ChatGptOpponent.Reply(200, JsonCodec.toJson(Map.of("status", "completed", "output",
                    List.of(Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", text)))))));
        });
        var session = TestSessions.create(g -> "draw", original);
        assertFalse(session.chatGptConfigured());
        var created = session.newGame("chatgpt", null, "normal", "entered-test-key");
        assertTrue(session.chatGptConfigured());
        assertFalse(original.configured(), "An in-flight opponent retains its original credentials");
        assertFalse(JsonCodec.toJson(created).contains("entered-test-key"));
        long version = ((Number) created.get("version")).longValue();
        assertFalse(JsonCodec.toJson(session.exportGame(version)).contains("entered-test-key"));
        // Убираем колоду: автоматический безопасный добор иначе не вызывает транспорт.
        var save = session.exportGame(version);
        save.put("cards", List.of(List.of(), List.of(), List.of(), List.of()));
        created = session.importGame(version, save);
        version = ((Number) created.get("version")).longValue();
        var actions = (List<?>) created.get("actions");
        String id = (String) ((Map<?, ?>) actions.getFirst()).get("id");
        var after = session.play(version, id);
        session.runAi(((Number) after.get("version")).longValue());
        assertEquals("entered-test-key", usedKey.get());
        var beforeInvalidKey = session.state();
        assertThrows(IllegalArgumentException.class, () -> session.newGame("chatgpt", null, "normal", "bad key"));
        assertEquals(beforeInvalidKey, session.state());
        assertTrue(session.chatGptConfigured());
    }
}
