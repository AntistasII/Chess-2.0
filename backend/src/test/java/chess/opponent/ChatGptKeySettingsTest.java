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
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var original = new ChatGptOpponent("", "test-model", "test", (key, body) -> {
            usedKey.set(key);
            calls.incrementAndGet();
            assertEquals("entered-test-key", key);
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
        for (int turn = 0; turn < 5; turn++) {
            var state = session.state();
            assertEquals("", state.get("result"));
            var moves = (List<?>) state.get("actions");
            String move = (String) ((Map<?, ?>) moves.getFirst()).get("id");
            var moved = session.play(((Number) state.get("version")).longValue(), move);
            session.runAi(((Number) moved.get("version")).longValue());
        }
        assertTrue(calls.get() >= 4, "Key must survive more than three genuine API decisions");
        assertEquals("entered-test-key", usedKey.get());
        var beforeInvalidKey = session.state();
        assertThrows(IllegalArgumentException.class, () -> session.newGame("chatgpt", null, "normal", "bad key"));
        assertEquals(beforeInvalidKey, session.state());
        assertTrue(session.chatGptConfigured());
    }
    @Test void firstAuthenticationFailureCanAppearAfterThreeLocallyChosenDraws() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var opponent = new ChatGptOpponent("old-config-key", "test-model", "test", (key, body) -> {
            calls.incrementAndGet();
            assertEquals("entered-test-key", key);
            return new ChatGptOpponent.Reply(401, "do not expose credentials");
        });
        var session = TestSessions.create(g -> "draw", opponent);
        var start = session.newGame("chatgpt", 3, "normal", "entered-test-key");
        var save = session.exportGame(((Number) start.get("version")).longValue());
        var deck = new java.util.ArrayList<Object>();
        for (int i = 0; i < 8; i++) deck.add(List.of(500 + i, "SHIELD"));
        save.put("cards", List.of(List.of(), List.of(), deck, List.of()));
        session.importGame(((Number) start.get("version")).longValue(), save);
        for (int turn = 0; turn < 3; turn++) {
            var state = session.state();
            var moved = session.play(((Number) state.get("version")).longValue(), turn == 0 ? "move:12:28:" : "draw");
            var reply = session.runAi(((Number) moved.get("version")).longValue());
            assertEquals(turn + 1, reply.get("enemyCards"));
            assertEquals(0, calls.get(), "Automatic draws do not validate the API key");
        }
        var state = session.state();
        var moved = session.play(((Number) state.get("version")).longValue(), "draw");
        var error = assertThrows(ChatGptOpponent.Failure.class,
                () -> session.runAi(((Number) moved.get("version")).longValue()));
        assertTrue(error.getMessage().contains("HTTP 401"));
        assertFalse(error.retryable());
        assertEquals(1, calls.get());
        assertTrue(session.chatGptConfigured(), "No code expires or deletes the entered key after three turns");
        assertEquals(moved, session.state(), "Authentication failure cannot change the position");
    }

}
