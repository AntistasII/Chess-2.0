package app.cardchess.mobile;

import android.content.Context;
import android.util.AtomicFile;
import chess.ai.Ai;
import chess.config.GameProperties;
import chess.game.GameSession;
import chess.opponent.ChatGptOpponent;
import chess.opponent.LocalAiOpponent;
import chess.serialization.JsonCodec;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** In-process engine. The web interface sends commands here; no HTTP server is started. */
final class OfflineGame {
    private final GameSession session;
    private final Context context;
    private final SecureApiKey keys;
    private final int retries;
    private final int timeoutMillis;
    private final String model;
    private final File currentFile;
    private final File manualFile;

    @SuppressWarnings("unchecked") OfflineGame(Context context) throws Exception {
        this.context = context.getApplicationContext();
        keys = new SecureApiKey(context);
        currentFile = new File(context.getFilesDir(), "current-game.json");
        manualFile = new File(context.getFilesDir(), "saved-game.json");
        Map<String, Object> defaults = JsonCodec.readObject(asset("defaults.json"));
        var config = (Map<String, Object>) ((Map<String, Object>) defaults.get("chess")).get("game");
        Map<String, GameProperties.Settings> modes = new LinkedHashMap<>();
        ((Map<String, Map<String, Object>>)config.get("opponents")).forEach((name, value) -> modes.put(name,
                new GameProperties.Settings(number(value, "hand-limit"), (String)value.get("difficulty"))));
        Map<String, GameProperties.SearchLimits> limits = new LinkedHashMap<>();
        ((Map<String, Map<String, Object>>)config.get("search")).forEach((name, value) -> limits.put(name,
                new GameProperties.SearchLimits(number(value, "depth"), number(value, "nodes"), number(value, "milliseconds"))));
        var settings = new GameProperties((String)config.get("back-rank"), "ai", modes, limits);
        var openAi = (Map<String, Object>)defaults.get("openai");
        model = (String)openAi.get("model");
        retries = number(openAi, "max-retries");
        timeoutMillis = durationMillis((String)openAi.get("response-timeout"));
        Ai ai = new Ai(settings);
        ChatGptOpponent chat = new ChatGptOpponent(keys.read(), model, asset("chatgpt-opponent.txt"), this::sendToOpenAi, ai);
        session = new GameSession(settings, "ai", new LocalAiOpponent(ai), chat);
        if (currentFile.exists()) {
            try { restore(currentFile, version()); }
            catch (IllegalArgumentException | IOException ignored) {
                // Keep a damaged snapshot for recovery; don't overwrite it until the next successful command.
            }
        }
    }
    private static int number(Map<String, Object> map, String key) { return ((Number)map.get(key)).intValue(); }
    private static int durationMillis(String value) {
        if (value.endsWith("ms")) return Integer.parseInt(value.substring(0, value.length() - 2));
        if (value.endsWith("s")) return Math.multiplyExact(1000, Integer.parseInt(value.substring(0, value.length() - 1)));
        throw new IllegalArgumentException("Unsupported response timeout");
    }
    private String asset(String name) throws IOException {
        try (var input = context.getAssets().open(name)) { return new String(readBytes(input, 2_000_000), StandardCharsets.UTF_8); }
    }
    private static byte[] readBytes(java.io.InputStream input, int limit) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > limit) throw new IOException("Слишком большой ответ");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
    private long version() { return ((Number)session.state().get("version")).longValue(); }

    Map<String, Object> execute(String path, Map<String, Object> input) throws Exception {
        if (path.equals("/api/ai")) {
            session.runAi(inputVersion(input));
            synchronized (session) { persist(currentFile); return state(); }
        }
        synchronized (session) {
            long version = inputVersion(input);
            switch (path) {
                case "/api/state": return state();
                case "/api/new": {
                    String mode = (String)input.getOrDefault("mode", "ai");
                    if (!Set.of("ai", "chatgpt", "human").contains(mode)) throw new IllegalArgumentException("Выберите соперника");
                    String apiKey = input.get("apiKey") instanceof String text ? text.trim() : "";
                    if (!apiKey.isEmpty() && (apiKey.length() > 1024 || apiKey.chars().anyMatch(Character::isWhitespace)))
                        throw new IllegalArgumentException("Проверьте введённый ключ ChatGPT");
                    session.newGame(mode, null, (String)input.getOrDefault("difficulty", null), apiKey);
                    if (mode.equals("chatgpt") && !apiKey.isEmpty()) keys.write(apiKey);
                    break;
                }
                case "/api/action": session.play(version, (String)input.get("actionId")); break;
                case "/api/scout": session.scout(version, number(input, "cardId")); break;
                case "/api/draw": session.claimDraw(version); break;
                case "/api/resign":
                    session.requireGame((String)input.get("gameId")); session.resign(version, true); break;
                case "/api/export": return session.exportGame(version);
                case "/api/import": {
                    @SuppressWarnings("unchecked") var data = (Map<String, Object>)input.get("save");
                    requireOfflineMode(data);
                    session.importGame(version, data); break;
                }
                case "/api/save": session.requireVersion(version); persist(manualFile); return state();
                case "/api/load": session.requireVersion(version); restore(manualFile, version); break;
                case "/api/reset": session.requireVersion(version); session.newGame("ai", null, null); break;
                default: throw new IllegalArgumentException("Не удалось выполнить действие");
            }
            persist(currentFile);
            return state();
        }
    }
    private static long inputVersion(Map<String, Object> input) { return input.get("version") instanceof Number n ? n.longValue() : -1; }
    private Map<String, Object> state() {
        Map<String, Object> state = session.state();
        state.put("networkOnly", false);
        state.put("apiKeyInputSupported", true);
        state.put("apiKeyEntry", true);
        state.put("openAiConfigured", session.chatGptConfigured());
        state.put("opponentRetryLimit", state.get("mode").equals("chatgpt") ? retries : 0);
        return state;
    }
    private void persist(File file) throws IOException {
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream stream = atomic.startWrite();
        try {
            stream.write(JsonCodec.toJson(session.exportGame(version())).getBytes(StandardCharsets.UTF_8));
            atomic.finishWrite(stream);
        } catch (IOException | RuntimeException e) { atomic.failWrite(stream); throw e; }
    }
    private void restore(File file, long version) throws IOException {
        AtomicFile atomic = new AtomicFile(file);
        byte[] bytes = atomic.readFully();
        if (bytes.length > 10_000_000) throw new IllegalArgumentException("Файл партии слишком большой");
        Map<String, Object> data = JsonCodec.readObject(new String(bytes, StandardCharsets.UTF_8));
        requireOfflineMode(data);
        session.importGame(version, data);
    }
    private static void requireOfflineMode(Map<String, Object> data) {
        if (data == null || !(data.get("settings") instanceof java.util.List<?> settings)
                || settings.isEmpty() || !Set.of("ai", "chatgpt", "human").contains(settings.get(0)))
            throw new IllegalArgumentException("Выберите сохранение локальной партии");
    }
    private ChatGptOpponent.Reply sendToOpenAi(String key, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL("https://api.openai.com/v1/responses").openConnection();
        connection.setConnectTimeout(timeoutMillis);
        connection.setReadTimeout(timeoutMillis);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + key);
        connection.setDoOutput(true);
        try {
            try (var stream = connection.getOutputStream()) { stream.write(body.getBytes(StandardCharsets.UTF_8)); }
            int status = connection.getResponseCode();
            try (var stream = status < 400 ? connection.getInputStream() : connection.getErrorStream()) {
                return new ChatGptOpponent.Reply(status, stream == null ? "{}" : new String(readBytes(stream, 2_000_000), StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }
}
