package chess.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;
import chess.game.Difficulty;
import chess.game.GameMode;

/** Настройки новой партии по типу соперника. Значения по умолчанию задаются в YAML. */
@ConfigurationProperties(prefix = "chess.game")
public record GameProperties(String backRank, String defaultMode, Map<String, Settings> opponents, Map<String, SearchLimits> search) {
    public GameProperties {
        if (backRank == null || !Arrays.equals(backRank.chars().sorted().toArray(), "BBKNNQRR".chars().toArray()))
            throw new IllegalArgumentException("chess.game.back-rank должен содержать 2 ладьи, 2 коня, 2 слона, ферзя и короля");
        if (opponents == null || !opponents.keySet().equals(ids(GameMode.values(), GameMode::id)))
            throw new IllegalArgumentException("Настройте ai, chatgpt, network и human в chess.game.opponents");
        opponents = Map.copyOf(opponents);
        if (defaultMode == null || !opponents.containsKey(defaultMode))
            throw new IllegalArgumentException("Неизвестный chess.game.default-mode");
        if (search == null || !search.keySet().equals(ids(Difficulty.values(), Difficulty::id)))
            throw new IllegalArgumentException("Настройте easy, normal и hard в chess.game.search");
        search = Map.copyOf(search);
    }

    /** Значения id всех режимов или сложностей: допустимые ключи настроек. */
    private static <E> Set<String> ids(E[] values, Function<E, String> id) {
        return Arrays.stream(values).map(id).collect(Collectors.toUnmodifiableSet());
    }

    public Settings forMode(String mode) {
        Settings settings = opponents.get(mode);
        if (settings == null) throw new IllegalArgumentException("Неизвестный режим: " + mode);
        return settings;
    }

    public SearchLimits searchFor(String difficulty) {
        SearchLimits limits = search.get(difficulty);
        if (limits == null) throw new IllegalArgumentException("Неизвестная сложность: " + difficulty);
        return limits;
    }

    /** Только данные конфигурации; выбор уровня не содержит значений по умолчанию в Java. */
    public record SearchLimits(int depth, int nodes, long milliseconds) {
        public SearchLimits {
            if (depth < 1 || depth > 64 || nodes < 1 || milliseconds < 1 || milliseconds > 60_000)
                throw new IllegalArgumentException("Поиск: глубина 1–64, узлы от 1, время 1–60000 мс");
        }
    }

    public record Settings(int handLimit, String difficulty) {
        public Settings {
            if (handLimit < 1 || handLimit > 12) throw new IllegalArgumentException("Лимит карт: от 1 до 12");
            if (difficulty == null || !ids(Difficulty.values(), Difficulty::id).contains(difficulty))
                throw new IllegalArgumentException("Неизвестная сложность");
        }
    }
}
