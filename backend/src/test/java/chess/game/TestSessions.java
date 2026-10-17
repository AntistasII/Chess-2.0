package chess.game;

import chess.config.GameProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;

/** Тестовые сессии используют те же значения YAML, что и приложение. */
public final class TestSessions {
    private TestSessions() {}

    public static Game game(String mode, int handLimit, String difficulty) {
        return new Game(mode, handLimit, difficulty, defaults().backRank());
    }

    public static chess.ai.Ai ai() { return new chess.ai.Ai(defaults()); }

    public static GameProperties defaults() {
        try {
            var environment = new StandardEnvironment();
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            for (var source : new YamlPropertySourceLoader().load("defaults", new ClassPathResource("application.yml")))
                environment.getPropertySources().addLast(source);
            return Binder.get(environment).bind("chess.game", Bindable.of(GameProperties.class)).get();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    public static GameSession create(MoveSelector local) { return create(local, unavailable()); }
    public static GameSession create(MoveSelector local, MoveSelector chatGpt) {
        var settings = defaults();
        return new GameSession(settings, settings.defaultMode(), local, chatGpt);
    }
    public static GameSession create(Game game, MoveSelector local) { return create(game, local, unavailable()); }
    public static GameSession create(Game game, MoveSelector local, MoveSelector chatGpt) {
        return new GameSession(game, defaults(), local, chatGpt);
    }
    private static MoveSelector unavailable() {
        return game -> { throw new IllegalArgumentException("ChatGPT не подключён к серверу"); };
    }
}
