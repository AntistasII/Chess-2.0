package chess.config;

import org.springframework.core.env.Environment;

/**
 * Настройки сервера; переменные Railway и прежние ключи окружения остаются совместимыми.
 */
public record ServerProperties(int port, String hostKey, String guestKey, java.nio.file.Path saveFile) {
    public ServerProperties(int port, String hostKey, String guestKey) {
        this(port, hostKey, guestKey, chess.game.SaveFile.DEFAULT);
    }

    public static ServerProperties from(Environment environment) {

        String cloudPort = environment.getProperty("PORT");

        int port = environment.getRequiredProperty("server.port", Integer.class);

        String hostKey = environment.getProperty("CHESS_HOST_KEY", "");
        String guestKey = environment.getProperty("CHESS_GUEST_KEY", "");

        if (cloudPort != null || !hostKey.isBlank()) {
            if (hostKey.length() < 16 || guestKey.length() < 16 || hostKey.equals(guestKey))
                throw new IllegalArgumentException("Задайте разные CHESS_HOST_KEY и CHESS_GUEST_KEY длиной от 16 символов.");
        }
        String saveFile = environment.getProperty("server.save-file", chess.game.SaveFile.DEFAULT.toString());
        return new ServerProperties(port, hostKey, guestKey, java.nio.file.Path.of(saveFile));
    }

    public boolean networkMode() {
        return !hostKey.isBlank();
    }

    @Override
    public String toString() {
        return "ServerProperties[port=" + port + ", hostKey=***, guestKey=***, saveFile=" + saveFile + "]";
    }
}
