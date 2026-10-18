package chess.opponent;

import chess.game.Game;
import chess.ai.TacticalHints;
import chess.ai.Ai;
import chess.ai.SearchResult;
import chess.ai.CardDrawPolicy;
import chess.serialization.JsonCodec;
import java.net.URI;
import java.nio.file.*;
import java.io.IOException;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Соперник через Responses API. Ключ берётся из конфигурации или окна новой партии в Railway. */
public final class ChatGptOpponent extends Opponent {
    private final String apiKey;
    private final String model;
    private final Transport transport;
    private final String prompt;
    private OpponentMemory memory;
    private final Ai advisor;

    @FunctionalInterface
    interface Transport { Reply send(String key, String body) throws Exception; }
    record Reply(int status, String body) {}

    ChatGptOpponent(String key, String model, String prompt, Transport transport) {
        this(key, model, prompt, transport, null);
    }

    ChatGptOpponent(String key, String model, String prompt, Transport transport, Ai advisor) {
        this.advisor = advisor;
        this.prompt = prompt;
        this.apiKey = key;
        this.model = model;
        this.transport = transport;
    }

    public static ChatGptOpponent fromConfig(chess.config.OpenAiProperties config) throws IOException {
        return fromConfig(config, null);
    }

    public static ChatGptOpponent fromConfig(chess.config.OpenAiProperties config, Ai advisor) throws IOException {
        String prompt = loadPrompt(Path.of("prompts/chatgpt-opponent.txt"));
        HttpClient client = HttpClient.newBuilder().connectTimeout(config.responseTimeout()).build();
        var opponent = new ChatGptOpponent(config.apiKey(), config.model(), prompt, (key, body) -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                    .timeout(config.responseTimeout()).header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return new Reply(response.statusCode(), response.body());
            } catch (java.net.http.HttpTimeoutException e) {
                throw new Failure("Истекло время ожидания ответа OpenAI (" + config.responseTimeout().toMillis() + " мс). Повторите запрос.");
            }
        }, advisor);
        opponent.memory = new OpponentMemory(Path.of("data/chatgpt-memory.json"));
        return opponent;
    }

    /** Новый ключ хранится только в памяти; настройки транспорта и память партии сохраняются. */
    public ChatGptOpponent withApiKey(String key) {
        var replacement = new ChatGptOpponent(key, model, prompt, transport, advisor);
        replacement.memory = memory;
        return replacement;
    }

    public boolean configured() { return apiKey != null && !apiKey.isBlank(); }

    static String loadPrompt(Path path) throws IOException {
        if (!Files.isRegularFile(path))
            throw new IOException("Не найден файл промпта: " + path + ". Запускайте сервер из корня проекта.");
        String prompt = Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
        if (prompt.isBlank()) throw new IOException("Файл промпта пуст: " + path);
        return prompt;
    }

    @Override public String mode() { return "chatgpt"; }
    @Override public boolean automatic() { return true; }
    @Override public boolean requiresScoutPreview() { return true; }

    @Override
    public String choose(Game position) {
        if (apiKey == null || apiKey.isBlank())
            throw new Failure("Для ChatGPT заполните openai.api-key в application.yml и перезапустите сервер.", false);
        var input = decisionInput(position);
        @SuppressWarnings("unchecked")
        var hints = (List<Map<String, Object>>) input.get("tacticalHints");
        Set<String> allowed = TacticalHints.saferActionIds(position, hints);
        SearchResult review = null;
        if (CardDrawPolicy.shouldDraw(position, hints, allowed)) allowed = Set.of("draw");
        else if (advisor != null && allowed.size() > 1) {
            review = advisor.analyze(position, allowed);
            var shortlist = review.competitiveActions();
            if (!shortlist.isEmpty()) allowed = shortlist;
        }
        final Set<String> candidates = allowed;
        if (review != null && !review.scores().isEmpty()) {
            var evaluations = new ArrayList<Object>();
            for (String id : candidates) evaluations.add(List.of(id, Math.round(review.scores().get(id))));
            input.put("search", Map.of("depth", review.depth(), "evaluations", evaluations));
        }
        @SuppressWarnings("unchecked")
        var drawStrategy = new LinkedHashMap<>((Map<String, Object>)input.get("cardStrategy"));
        if (Boolean.TRUE.equals(drawStrategy.get("drawAvailable")) && !candidates.contains("draw")) {
            drawStrategy.put("drawAvailable", false);
            drawStrategy.put("priority", "tactical_action");
        }
        input.put("cardStrategy", drawStrategy);
        var actions = position.legalActions().stream().filter(a -> candidates.contains(a.id())).toList();
        @SuppressWarnings("unchecked")
        var actionViews = (List<Map<String, Object>>) input.get("actions");
        input.put("actions", actionViews.stream().filter(a -> candidates.contains(a.get("id"))).toList());
        input.put("tacticalHints", hints.stream().filter(h -> candidates.contains(h.get("actionId"))).toList());
        if (actions.isEmpty()) throw new Failure("У ChatGPT нет допустимых действий.");
        var schema = Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of("actionId", Map.of("type", "string")),
                "required", List.of("actionId"));
        input.put("learnedLessons", memory == null ? List.of() : memory.observe(position));
        if (actions.size() == 1) return actions.get(0).id();
        var request = new LinkedHashMap<String, Object>(Map.of("model", model, "store", false,
                "instructions", prompt, "input", JsonCodec.toJson(compactInput(input)),
                "prompt_cache_key", "card-chess-v2",
                "max_output_tokens", 2048, "text", Map.of("format", Map.of(
                        "type", "json_schema", "name", "chess_action", "strict", true, "schema", schema))));
        // Luna иначе использует medium: для короткого выбора хода отключаем рассуждение.
        if (model.equals("gpt-6-luna")) request.put("reasoning", Map.of("effort", "none"));
        String body = JsonCodec.toJson(request);
        try {
            Reply response = transport.send(apiKey, body);
            if (response.status() != 200) throw new Failure(switch (response.status()) {
                case 401 -> "OpenAI отклонил API-ключ (HTTP 401). Укажите действующий ключ в окне новой партии или в openai.api-key в application.yml. Предыдущие автоматические действия могли выполняться без запроса к OpenAI.";
                case 403 -> "OpenAI запретил доступ (HTTP 403). Проверьте разрешения API-ключа и доступ к модели.";
                case 429 -> "OpenAI: исчерпан лимит или баланс API. Проверьте аккаунт и повторите позже.";
                case 400, 404 -> "OpenAI не принял запрос. Проверьте openai.model в application.yml и доступность модели.";
                default -> "OpenAI временно недоступен (HTTP " + response.status() + "). Повторите запрос.";
            }, !Set.of(400, 401, 403, 404).contains(response.status()));
            Map<String, Object> result = JsonCodec.readObject(response.body());
            if (!"completed".equals(result.get("status"))) throw new Failure("ChatGPT не завершил ответ. Повторите запрос.");
            if (result.get("usage") instanceof Map<?, ?> usage) {
                Object cached = usage.get("input_tokens_details") instanceof Map<?, ?> details
                        ? details.get("cached_tokens") : 0;
                System.out.println("OpenAI tokens: input=" + usage.get("input_tokens")
                        + ", cached=" + cached + ", output=" + usage.get("output_tokens"));
            }
            StringBuilder answer = new StringBuilder();
            if (result.get("output") instanceof List<?> output) for (Object item : output) {
                if (!(item instanceof Map<?, ?> message) || !"message".equals(message.get("type"))) continue;
                if (message.get("content") instanceof List<?> content) for (Object block : content) {
                    if (block instanceof Map<?, ?> part && "output_text".equals(part.get("type"))
                            && part.get("text") instanceof String text) answer.append(text);
                }
            }
            Object actionId = JsonCodec.readObject(answer.toString()).get("actionId");
            if (!(actionId instanceof String id) || actions.stream().noneMatch(a -> a.id().equals(id)))
                throw new Failure("ChatGPT вернул недопустимый ход. Повторите запрос.");
            return id;
        } catch (Failure e) { throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Failure("Запрос к ChatGPT прерван. Повторите запрос.");
        } catch (java.io.IOException e) {
            throw new Failure("Не удалось связаться с OpenAI. Проверьте интернет и повторите запрос.");
        } catch (Exception e) {
            // Не выводим тело ответа или ключ в логи и API.
            throw new Failure("Не удалось прочитать ответ ChatGPT. Повторите запрос.");
        }
    }

    /** Явно выделяем добор, чтобы модель не теряла его среди шахматных ходов. */
    static Map<String, Object> decisionInput(Game position) {
        var input = new LinkedHashMap<String, Object>(position.opponentState());
        input.put("history", position.moveHistory());
        boolean canDraw = position.legalActions().stream().anyMatch(a -> a.id().equals("draw"));
        int cards = position.cards(position.whiteTurn()).size();
        input.put("tacticalHints", TacticalHints.forPosition(position));
        input.put("cardStrategy", Map.of(
                "drawAvailable", canDraw,
                "ownCards", cards,
                "targetCards", input.get("handLimit"),
                "priority", !canDraw ? "draw_unavailable" : cards == 0 ? "refill_empty_hand"
                        : "refill_hand"));
        return input;
    }

    /** Удаляем поля интерфейса и дубли: допустимые id находятся только в строках candidates. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> compactInput(Map<String, Object> source) {
        var out = new LinkedHashMap<String, Object>();
        for (String key : List.of("boardSize", "castlingRights", "whiteTurn", "ply", "handLimit", "enemyCards",
                "deckCount", "discardCount", "check", "cardStrategy", "search", "squareEffects")) out.put(key, source.get(key));
        out.put("board", ((List<Map<String, Object>>) source.get("board")).stream().map(p -> List.of(
                p.get("square"), p.get("type"), Boolean.TRUE.equals(p.get("white")) ? "W" : "B",
                (Boolean.TRUE.equals(p.get("boost")) ? "b" : "")
                    + (Boolean.TRUE.equals(p.get("shield")) ? "s" : "")
                    + (Boolean.TRUE.equals(p.get("frozen")) ? "f" + p.getOrDefault("frozenTurns", 1) : "")
                    + (Boolean.TRUE.equals(p.get("astral")) ? "a" + p.get("astralTurns") : ""))).toList());
        for (String key : List.of("hand", "scouting"))
            out.put(key, ((List<Map<String, Object>>) source.get(key)).stream()
                    .map(c -> List.of(c.get("id"), c.get("type"), c.getOrDefault("durationTurns", 0))).toList());
        out.put("captured", ((List<Map<String, Object>>) source.get("captured")).stream()
                .map(p -> List.of(p.get("type"), Boolean.TRUE.equals(p.get("white")) ? "W" : "B")).toList());
        out.put("candidates", ((List<Map<String, Object>>) source.get("tacticalHints")).stream()
                .map(h -> List.of(h.get("actionId"), h.get("immediateMaterialDelta"),
                        h.get("worstMaterialDeltaAfterOneReply"), h.get("dangerousReplyId"),
                        h.getOrDefault("mateInOneReplyId", ""),
                        h.getOrDefault("worstMaterialDeltaAfterRecapture", h.get("worstMaterialDeltaAfterOneReply")),
                        h.getOrDefault("mateInOne", false), h.getOrDefault("mateThreat", false),
                        h.getOrDefault("checkingForkReplyId", ""))).toList());
        out.put("history", ((List<String>) source.get("history")).stream().map(ChatGptOpponent::compactHistory).toList());
        List<String> lessons = (List<String>) source.getOrDefault("learnedLessons", List.of());
        out.put("learnedLessons", lessons.subList(Math.max(0, lessons.size() - 8), lessons.size()).stream()
                .map(ChatGptOpponent::compactHistory).toList());
        return out;
    }

    private static String compactHistory(String text) {
        return text.replace("Белые: ", "W:").replace("Чёрные: ", "B:")
                .replace("Пешка ", "P").replace("Конь ", "N").replace("Слон ", "B")
                .replace("Ладья ", "R").replace("Ферзь ", "Q").replace("Король ", "K")
                .replace(" → ", "-").replace("взяли карту", "draw");
    }

    public static final class Failure extends RuntimeException {
        private final boolean retryable;

        public Failure(String message) { this(message, true); }
        public Failure(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }
        public boolean retryable() { return retryable; }
    }

}
