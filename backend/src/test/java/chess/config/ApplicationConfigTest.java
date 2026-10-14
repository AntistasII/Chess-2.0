package chess.config;

import java.nio.file.*;
import java.util.Map;

public final class ApplicationConfigTest {
    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("chess-config-");
        Path file = folder.resolve("application.yml");
        try {
            var env = Map.of("OPENAI_API_KEY", "env-key", "OPENAI_MODEL", "env-model");
            assert ApplicationConfig.load(file, env).apiKey().equals("env-key");
            Files.writeString(file, "# settings\nopenai:\n  api-key: \"file-key#value\" # comment\n  model: 'file-model'\n");
            var config = ApplicationConfig.load(file, env);
            assert config.apiKey().equals("file-key#value") && config.model().equals("file-model");
            Files.writeString(file, "openai:\n  api-key: \"\"\n  model: \"\"\n");
            assert ApplicationConfig.load(file, env).apiKey().equals("env-key");
            assert ApplicationConfig.load(file, Map.of()).model().equals("gpt-4.1-mini");
            for (String bad : new String[]{"openai:\n  api-key: \"secret", "openai:\n  unknown: secret", "openai:\n  model: a\n  model: secret"}) {
                Files.writeString(file, bad);
                try { ApplicationConfig.load(file, env); throw new AssertionError("Bad config accepted"); }
                catch (java.io.IOException e) { assert !e.getMessage().contains("secret"); }
            }
            System.out.println("Config checks passed: file priority, env fallback, quotes, comments and safe errors.");
        } finally { Files.deleteIfExists(file); Files.delete(folder); }
    }
}
