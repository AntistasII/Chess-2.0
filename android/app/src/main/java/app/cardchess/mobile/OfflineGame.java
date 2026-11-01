package app.cardchess.mobile;

import android.content.Context;
import android.util.AtomicFile;
import chess.ai.Ai;
import chess.config.GameProperties;
import chess.game.GameSession;
import chess.opponent.LocalAiOpponent;
import chess.serialization.JsonCodec;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** In-process engine. The web interface sends commands here; no HTTP server is started. */
final class OfflineGame {
    private final GameSession session;
    private final Context context;
    private final File currentFile;
    private final File manualFile;

    @SuppressWarnings("unchecked") OfflineGame(Context context) throws Exception {
        this.context = context.getApplicationContext();
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
        Ai ai = new Ai(settings);
        session = new GameSession(settings, "ai", new LocalAiOpponent(ai), game -> {
            throw new IllegalArgumentException("Выберите игру с компьютером или другом");
        });
        if (currentFile.exists()) {
            try { restore(currentFile, version()); }
            catch (IllegalArgumentException | IOException ignored) {
                // Keep a damaged snapshot for recovery; don't overwrite it until the next successful command.
            }
        }
    }
    private static int number(Map<String, Object> map, String key) { return ((Number)map.get(key)).intValue(); }
    private String asset(String name) throws IOException {
        try (var input = context.getAssets().open(name);
             var output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
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
                    if (!Set.of("ai", "human").contains(mode)) throw new IllegalArgumentException("Выберите соперника");
                    session.newGame(mode, null, (String)input.getOrDefault("difficulty", null));
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
        state.put("offlineApp", true);
        state.put("apiKeyInputSupported", false);
        state.put("apiKeyEntry", false);
        state.put("openAiConfigured", false);
        state.put("opponentRetryLimit", 0);
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
                || settings.isEmpty() || !Set.of("ai", "human").contains(settings.get(0)))
            throw new IllegalArgumentException("Выберите сохранение локальной партии");
    }
}
