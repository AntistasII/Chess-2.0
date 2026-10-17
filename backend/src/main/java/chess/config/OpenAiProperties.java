package chess.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

/**
 * Spring заполняет настройки из application.yml и окружения.
 */
@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(String apiKey, String model, Duration responseTimeout, int maxRetries) {
    public OpenAiProperties {
        apiKey = apiKey == null ? "" : apiKey;
        if (responseTimeout == null || responseTimeout.isZero() || responseTimeout.isNegative())
            throw new IllegalArgumentException("openai.response-timeout должен быть больше нуля");
        if (maxRetries < 0 || maxRetries > 100)
            throw new IllegalArgumentException("openai.max-retries: от 0 до 100");
        if (model == null || model.isBlank())
            throw new IllegalArgumentException("Задайте openai.model в application.yml или OPENAI_MODEL");
    }

    @Override
    public String toString() {
        return "OpenAiProperties[apiKey=***, model=" + model + "]";
    }
}
