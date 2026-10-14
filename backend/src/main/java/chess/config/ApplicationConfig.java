package chess.config;

import chess.json.Json;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Читает небольшой YAML-конфиг: секция openai с api-key и model, строковые значения. */
public record ApplicationConfig(String apiKey, String model) {
    public static ApplicationConfig load(Path file) throws IOException {
        return load(file, System.getenv());
    }

    static ApplicationConfig load(Path file, Map<String, String> environment) throws IOException {
        Map<String, String> values = new HashMap<>();
        if (Files.exists(file)) {
            boolean section = false;
            int number = 0;
            for (String line : Files.readAllLines(file)) {
                number++;
                String text = line.strip();
                if (text.isEmpty() || text.startsWith("#")) continue;
                try {
                    if (line.indexOf('\t') >= 0) throw new IllegalArgumentException();
                    if (!line.startsWith(" ")) {
                        if (!text.matches("openai:\\s*(#.*)?") || section) throw new IllegalArgumentException();
                        section = true;
                        continue;
                    }
                    if (!section) throw new IllegalArgumentException();
                    int colon = text.indexOf(':');
                    if (colon < 0) throw new IllegalArgumentException();
                    String key = text.substring(0, colon);
                    if (!Set.of("api-key", "model").contains(key) || values.containsKey(key)) throw new IllegalArgumentException();
                    values.put(key, scalar(text.substring(colon + 1).strip()));
                } catch (RuntimeException e) {
                    // Не включаем строку конфига с возможным секретом в сообщение.
                    throw new IOException("Некорректный application.yml, строка " + number
                            + ". Поддерживается секция openai с api-key и model (строки). ");
                }
            }
        }
        String key = values.getOrDefault("api-key", "");
        String model = values.getOrDefault("model", "");
        // Явные значения файла имеют приоритет; окружение остаётся запасным способом настройки.
        if (key.isBlank()) key = environment.getOrDefault("OPENAI_API_KEY", "");
        if (model.isBlank()) model = environment.getOrDefault("OPENAI_MODEL", "gpt-4.1-mini");
        if (model.isBlank()) model = "gpt-4.1-mini";
        return new ApplicationConfig(key, model);
    }

    private static String scalar(String text) {
        if (text.startsWith("\"") || text.startsWith("'")) {
            char quote = text.charAt(0);
            int end = 1;
            for (; end < text.length(); end++) {
                char c = text.charAt(end);
                if (quote == '"' && c == '\\') { end++; continue; }
                if (c == quote) {
                    if (quote == '\'' && end + 1 < text.length() && text.charAt(end + 1) == '\'') { end++; continue; }
                    break;
                }
            }
            if (end >= text.length()) throw new IllegalArgumentException();
            String tail = text.substring(end + 1).strip();
            if (!tail.isEmpty() && !tail.startsWith("#")) throw new IllegalArgumentException();
            String literal = text.substring(0, end + 1);
            return quote == '"' ? (String) Json.object("{\"v\":" + literal + "}").get("v")
                    : literal.substring(1, literal.length() - 1).replace("''", "'");
        }
        int comment = text.indexOf(" #");
        if (text.startsWith("#")) return "";
        if (comment >= 0) text = text.substring(0, comment).strip();
        if (text.matches(".*[\\[\\]{}&*!|>].*")) throw new IllegalArgumentException();
        return text;
    }
}
