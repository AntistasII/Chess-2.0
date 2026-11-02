package chess.game;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Локальный файл кнопок «Сохранить» и «Продолжить». Содержит полный снимок,
 * включая скрытые карты, поэтому никогда не отдаётся в API.
 */
public final class SaveFile {
    /** Путь по умолчанию относительно рабочего каталога сервера. */
    public static final Path DEFAULT = Path.of("data/saved-game.json");

    private final Path path;

    public SaveFile(Path path) { this.path = Objects.requireNonNull(path); }

    public Path path() { return path; }

    /** Запись через временный файл: при сбое прежнее сохранение не повреждается. */
    void write(Game game) throws IOException { GameSave.write(game, path); }

    Game read() throws IOException { return GameSave.read(path); }
}
