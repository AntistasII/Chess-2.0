package chess.config;

import chess.opponent.LocalAiOpponent;
import chess.ai.Ai;
import chess.opponent.ChatGptOpponent;
import chess.cards.CardRegistry;
import chess.game.GameSession;
import chess.server.GameServer;
import chess.service.GameService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.IOException;

/**
 * Создаёт зависимости приложения; Spring запускает и останавливает сервер.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({OpenAiProperties.class, GameProperties.class})
public class ApplicationConfig {
    @Bean
    ServerProperties serverProperties(Environment environment) {
        return ServerProperties.from(environment);
    }

    @Bean
    CardRegistry cardRegistry() {
        return CardRegistry.defaultRegistry();
    }

    @Bean
    GameSession gameSession(CardRegistry registry, OpenAiProperties openAi, ServerProperties properties, GameProperties gameProperties) throws IOException {
        // Зависимость от registry гарантирует проверку всех карт до создания партии.
        var localAi = new Ai(gameProperties);
        return new GameSession(gameProperties, properties.networkMode() ? "network" : gameProperties.defaultMode(),
                new LocalAiOpponent(localAi), ChatGptOpponent.fromConfig(openAi, localAi));
    }

    @Bean
    GameService gameService(GameSession session, ServerProperties properties, OpenAiProperties openAi) {
        return new GameService(session, properties.hostKey(), properties.guestKey(), openAi);
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    GameServer gameServer(GameService service, ServerProperties properties) throws IOException {
        return new GameServer(properties.port(), service);
    }
}
