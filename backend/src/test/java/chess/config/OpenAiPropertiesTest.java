package chess.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiPropertiesTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OpenAiProperties.class)
    static class Settings {}

    private OpenAiProperties bind(String yaml, Map<String, Object> environment) throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            var sources = context.getEnvironment().getPropertySources();
            sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            sources.addFirst(new SystemEnvironmentPropertySource("test-env", environment));
            var loader = new YamlPropertySourceLoader();
            if (yaml != null) for (var source : loader.load("test-file", new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8))))
                sources.addLast(source);
            for (var source : loader.load("defaults", new ClassPathResource("application.yml"))) sources.addLast(source);
            context.register(Settings.class); context.refresh();
            return context.getBean(OpenAiProperties.class);
        }
    }

    @Test void defaultsWithoutKey() throws Exception {
        var settings = bind(null, Map.of());
        assertEquals("", settings.apiKey());
        assertEquals("gpt-6-luna", settings.model());
        assertEquals(java.time.Duration.ofSeconds(10), settings.responseTimeout());
        assertEquals(10, settings.maxRetries());
    }

    @Test void timeoutAndRetryPolicyCanBeOverridden() throws Exception {
        var settings = bind("openai:\n  response-timeout: 3s\n  max-retries: 2\n", Map.of());
        assertEquals(java.time.Duration.ofSeconds(3), settings.responseTimeout());
        assertEquals(2, settings.maxRetries());
        assertThrows(org.springframework.beans.BeansException.class,
                () -> bind("openai:\n  response-timeout: 0s\n", Map.of()));
        assertThrows(org.springframework.beans.BeansException.class,
                () -> bind("openai:\n  max-retries: -1\n", Map.of()));
    }

    @Test void railwayEnvironmentAliases() throws Exception {
        var settings = bind(null, Map.of("OPENAI_API_KEY", "test-env-key", "OPENAI_MODEL", "test-model"));
        assertEquals("test-env-key", settings.apiKey()); assertEquals("test-model", settings.model());
        assertFalse(settings.toString().contains("test-env-key"));
    }

    @Test void nativeYamlBinding() throws Exception {
        var settings = bind("openai:\n  api-key: \"file-key#value\" # comment\n  model: 'file-model'\nspring:\n  main:\n    banner-mode: off\n", Map.of());
        assertEquals("file-key#value", settings.apiKey()); assertEquals("file-model", settings.model());
    }

    @Test void rejectsExplicitBlankModel() {
        assertThrows(org.springframework.beans.BeansException.class,
                () -> bind("openai:\n  api-key: ''\n  model: ''\n", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new OpenAiProperties(null, null, null, 0));
    }

    @Test void supportsEnvironmentPlaceholdersInExternalYaml() throws Exception {
        var settings = bind("openai:\n  api-key: '${OPENAI_API_KEY:}'\n  model: '${OPENAI_MODEL:gpt-4.1-mini}'\n",
                Map.of("OPENAI_API_KEY", "test-placeholder-key", "OPENAI_MODEL", "test-placeholder-model"));
        assertEquals("test-placeholder-key", settings.apiKey());
        assertEquals("test-placeholder-model", settings.model());
    }
}
