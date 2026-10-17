package chess.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ServerPropertiesTest {
    private static class TestEnvironment extends StandardEnvironment {
        private final Map<String, Object> values = new HashMap<>();
        TestEnvironment() {
            getPropertySources().remove(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            getPropertySources().remove(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            getPropertySources().addFirst(new MapPropertySource("test", values));
            try {
                for (var source : new YamlPropertySourceLoader().load("defaults", new ClassPathResource("application.yml")))
                    getPropertySources().addLast(source);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        TestEnvironment withProperty(String name, String value) {
            values.put(name, value);
            return this;
        }
    }

    @Test void localDefaultsAndExplicitPort() {
        var environment = new TestEnvironment();
        assertEquals(8080, ServerProperties.from(environment).port());
        assertFalse(ServerProperties.from(environment).networkMode());
        environment.withProperty("PORT", "9000")
                .withProperty("CHESS_HOST_KEY", "host-secret-123456")
                .withProperty("CHESS_GUEST_KEY", "guest-secret-123456");
        assertEquals(9000, ServerProperties.from(environment).port());
        environment.withProperty("server.port", "8084");
        assertEquals(8084, ServerProperties.from(environment).port());
        assertTrue(ServerProperties.from(environment).networkMode());
    }

    @Test void cloudRequiresDifferentStrongKeysAndHidesThem() {
        var environment = new TestEnvironment().withProperty("PORT", "9000");
        assertThrows(IllegalArgumentException.class, () -> ServerProperties.from(environment));
        environment.withProperty("CHESS_HOST_KEY", "same-secret-123456")
                .withProperty("CHESS_GUEST_KEY", "same-secret-123456");
        assertThrows(IllegalArgumentException.class, () -> ServerProperties.from(environment));
        environment.withProperty("CHESS_GUEST_KEY", "other-secret-123456");
        var properties = ServerProperties.from(environment);
        assertFalse(properties.toString().contains(properties.hostKey()));
        assertFalse(properties.toString().contains(properties.guestKey()));
    }
}
