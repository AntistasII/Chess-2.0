package chess.ai;

import chess.game.Game;
import chess.json.Json;
import java.util.*;

/** Проверка протокола OpenAI без ключа и платных запросов. */
public final class ChatGptOpponentTest {
    private static int checks;
    private static void check(boolean ok) { checks++; if (!ok) throw new AssertionError(); }
    private static String completed(String text) {
        return Json.stringify(Map.of("status", "completed", "output", List.of(
                Map.of("type", "reasoning", "summary", List.of()),
                Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", text))))));
    }
    public static void main(String[] args) throws Exception {
        String filePrompt = ChatGptOpponent.loadPrompt(java.nio.file.Path.of("prompts/chatgpt-opponent.txt"));
        check(filePrompt.contains("Обязательная тактическая проверка") && filePrompt.contains("e5-f6-g7"));
        java.nio.file.Path empty = java.nio.file.Files.createTempFile("chess-empty-prompt", ".txt");
        try {
            try { ChatGptOpponent.loadPrompt(empty); throw new AssertionError(); }
            catch (java.io.IOException expected) { check(expected.getMessage().contains("пуст")); }
            java.nio.file.Files.delete(empty);
            try { ChatGptOpponent.loadPrompt(empty); throw new AssertionError(); }
            catch (java.io.IOException expected) { check(expected.getMessage().contains("Не найден")); }
        } finally { java.nio.file.Files.deleteIfExists(empty); }
        Game game = new Game("chatgpt", 3, "normal");
        var original = ChatGptOpponent.decisionInput(game);
        original.put("learnedLessons", Collections.nCopies(30, "past lesson"));
        var compact = ChatGptOpponent.compactInput(original);
        check(((List<?>) compact.get("learnedLessons")).size() == 8);
        int before = Json.stringify(original).length(), after = Json.stringify(compact).length();
        check(after < before / 2);
        System.out.println("Position JSON characters: " + before + " -> " + after);
        game.play("move:12:28:");
        var withHistory = ChatGptOpponent.compactInput(ChatGptOpponent.decisionInput(game));
        check(((List<?>) withHistory.get("history")).equals(List.of("W:Pe2-e4")));
        game = new Game("chatgpt", 3, "normal");
        Game refill = new Game("chatgpt", 3, "normal");
        for (int cards = 1; cards <= 3; cards++) {
            refill.play("draw");
            refill.play(cards % 2 == 1 ? "move:57:42:" : "move:42:57:");
            Map<?, ?> strategy = (Map<?, ?>) ChatGptOpponent.decisionInput(refill).get("cardStrategy");
            check(strategy.get("ownCards").equals(cards));
            check(strategy.get("targetCards").equals(3));
            check(strategy.get("priority").equals(cards < 3 ? "refill_hand" : "draw_unavailable"));
            check(strategy.get("drawAvailable").equals(cards < 3));
        }
        var good = new ChatGptOpponent("test-secret", "test-model", "Test instructions", (key, body) -> {
            Map<String, Object> request = Json.object(body);
            check(key.equals("test-secret") && !body.contains(key));
            check(request.get("instructions").equals("Test instructions"));
            check(request.get("store").equals(false));
            check(request.get("prompt_cache_key").equals("card-chess-v2"));
            check(request.get("model").equals("test-model"));
            Map<String, Object> input = Json.object((String) request.get("input"));
            Map<?, ?> strategy = (Map<?, ?>) input.get("cardStrategy");
            check(strategy.get("drawAvailable").equals(true) && strategy.get("priority").equals("refill_empty_hand"));
            check(!input.containsKey("actions") && !input.containsKey("tacticalHints"));
            check(((List<?>) input.get("board")).size() == 32);
            check(((List<?>) input.get("candidates")).stream().anyMatch(row -> ((List<?>) row).get(0).equals("draw")));
            check(!input.containsKey("deck") && !input.containsKey("blackHand"));
            return new ChatGptOpponent.Reply(200, completed("{\"actionId\":\"draw\"}"));
        });
        check(good.choose(game).equals("draw"));
        for (var reply : List.of(new ChatGptOpponent.Reply(401, "secret"),
                new ChatGptOpponent.Reply(429, "secret"), new ChatGptOpponent.Reply(500, "secret"),
                new ChatGptOpponent.Reply(200, completed("{\"actionId\":\"invalid\"}")),
                new ChatGptOpponent.Reply(200, "{\"status\":\"incomplete\"}"),
                new ChatGptOpponent.Reply(200, "bad JSON"),
                new ChatGptOpponent.Reply(200, completed("{}")))) {
            try { new ChatGptOpponent("test-secret", "test", "Test instructions", (key, body) -> reply).choose(game); throw new AssertionError(); }
            catch (ChatGptOpponent.Failure e) { check(!e.getMessage().contains("secret")); }
        }
        try { new ChatGptOpponent(null, "test", "Test instructions", (key, body) -> { throw new AssertionError(); }).choose(game); throw new AssertionError(); }
        catch (ChatGptOpponent.Failure e) { check(e.getMessage().contains("application.yml")); }
        System.out.println("Passed " + checks + " ChatGPT API checks (no external requests).");
    }
}
