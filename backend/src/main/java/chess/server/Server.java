package chess.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа: запускает Spring-контекст приложения.
 */
@SpringBootApplication(scanBasePackages = "chess", proxyBeanMethods = false)
public class Server {
    public static void main(String[] args) {
        // Сохраняем запуск из scripts/start.sh с номером порта без имени параметра.
        if (args.length == 1 && args[0].matches("[0-9]+"))
            args = new String[]{"--server.port=" + args[0]};

        var application = new SpringApplication(Server.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.run(args);
    }
}
