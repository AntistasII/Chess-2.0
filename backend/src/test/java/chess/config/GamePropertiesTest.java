package chess.config;

import chess.game.GameSession;
import chess.game.TestSessions;
import chess.model.ApiRequest;
import chess.model.ClientIdentity;
import chess.model.GameOperation;
import chess.service.GameService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GamePropertiesTest {
    private GameProperties bind(Map<String, Object> overrides) throws Exception {
        var environment = new StandardEnvironment();
        var sources = environment.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.addFirst(new MapPropertySource("external-settings", overrides));
        for (var source : new YamlPropertySourceLoader().load("defaults", new ClassPathResource("application.yml")))
            sources.addLast(source);
        return Binder.get(environment).bind("chess.game", Bindable.of(GameProperties.class)).get();
    }

    @Test void classpathYamlContainsDefaultsForEveryOpponent() {
        var properties = TestSessions.defaults();
        assertEquals("RNBQKBNR", properties.backRank());
        assertEquals("ai", properties.defaultMode());
        for (String mode : new String[]{"ai", "chatgpt", "network", "human"}) {
            assertEquals(3, properties.forMode(mode).handLimit());
            assertEquals("normal", properties.forMode(mode).difficulty());
        }
    }

    @Test void partialOverridesApplyToStartupNewGamesAndNetworkReset() throws Exception {
        var properties = bind(Map.of("chess.game.default-mode", "human",
                "chess.game.opponents.ai.hand-limit", 4,
                "chess.game.opponents.chatgpt.hand-limit", 5,
                "chess.game.opponents.network.hand-limit", 6,
                "chess.game.opponents.human.hand-limit", 7,
                "chess.game.opponents.ai.difficulty", "hard"));
        var session = new GameSession(properties, properties.defaultMode(), g -> "draw", g -> "draw");
        assertEquals("human", session.state().get("mode"));
        assertEquals(7, session.state().get("handLimit"));
        var service = new GameService(session, "", "");
        var owner = new ClientIdentity(true, null);
        for (String mode : new String[]{"ai", "chatgpt", "human", "network"}) {
            service.execute(owner, new ApiRequest(GameOperation.NEW_GAME, true, Map.of("mode", mode)));
            assertEquals(properties.forMode(mode).handLimit(), session.state().get("handLimit"));
            assertEquals(properties.forMode(mode).difficulty(), session.state().get("difficulty"));
        }
        service.execute(owner, new ApiRequest(GameOperation.RESET, true,
                Map.of("version", session.state().get("version"))));
        assertTrue(session.isNetwork());
        assertEquals(6, session.state().get("handLimit"));
        service.execute(owner, new ApiRequest(GameOperation.NEW_GAME, true, Map.of()));
        assertEquals("human", session.state().get("mode"));
        assertEquals(7, session.state().get("handLimit"));
    }

    @Test void explicitChoicesAndSavedSettingsOverrideDefaults() throws Exception {
        var properties = bind(Map.of("chess.game.opponents.ai.hand-limit", 5));
        var session = new GameSession(properties, "ai", g -> "draw", g -> "draw");
        session.newGame("ai", 9, "easy");
        var saved = session.exportGame(1);
        session.newGame("ai", null, null);
        assertEquals(5, session.state().get("handLimit"));
        session.importGame(2, saved);
        assertEquals(9, session.state().get("handLimit"));
        assertEquals("easy", session.state().get("difficulty"));
    }

    @Test void invalidConfigurationFailsAtBinding() {
        assertThrows(RuntimeException.class, () -> bind(Map.of("chess.game.back-rank", "")));
        assertThrows(RuntimeException.class, () -> bind(Map.of("chess.game.back-rank", "RNBQKBNP")));
        assertThrows(RuntimeException.class, () -> bind(Map.of("chess.game.opponents.ai.hand-limit", 13)));
        assertThrows(RuntimeException.class, () -> bind(Map.of("chess.game.opponents.ai.difficulty", "impossible")));
        assertThrows(RuntimeException.class, () -> bind(Map.of("chess.game.default-mode", "missing")));
    }

    @Test void searchLimitsBindFromYamlAndAllowPartialOverrides() throws Exception {
        var properties = bind(Map.of("chess.game.search.normal.depth", 3,
                "chess.game.search.normal.nodes", 800,
                "chess.game.search.normal.milliseconds", 700));
        assertEquals(new GameProperties.SearchLimits(3, 800, 700), properties.searchFor("normal"));
        assertEquals(new GameProperties.SearchLimits(2, 250, 500), properties.searchFor("easy"));
        assertEquals(new GameProperties.SearchLimits(5, 4500, 3000), properties.searchFor("hard"));
        assertThrows(IllegalArgumentException.class, () -> properties.searchFor("missing"));
    }

    @Test void invalidSearchBudgetsFailAtStartup() {
        for (String field : new String[]{"depth", "nodes", "milliseconds"})
            assertThrows(RuntimeException.class, () -> bind(Map.of("chess.game.search.normal." + field, 0)));
        assertThrows(RuntimeException.class, () -> bind(Map.of("chess.game.search.normal.milliseconds", Long.MAX_VALUE)));
    }
}
