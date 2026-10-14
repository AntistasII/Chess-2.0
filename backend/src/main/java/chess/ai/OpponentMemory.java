package chess.ai;

import chess.game.Game;
import chess.json.Json;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Локальная память наблюдений о потерях фигур; не обучение весов модели. */
final class OpponentMemory {
    private final Path file;
    private final List<String> lessons = new ArrayList<>();
    private String previousGame = "";
    private int previousPly;
    private Map<Integer, String> previousPieces = Map.of();
    private boolean previousSide;

    OpponentMemory(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) {
            Object stored = Json.object(Files.readString(file)).get("lessons");
            if (!(stored instanceof List<?> list)) throw new IOException("Неверный формат памяти ChatGPT");
            for (Object item : list) {
                if (!(item instanceof String text)) throw new IOException("Неверный формат памяти ChatGPT");
                lessons.add(text);
            }
            trim();
        }
    }

    synchronized List<String> observe(Game game) {
        Map<Integer, String> current = new HashMap<>();
        for (int s = 0; s < game.boardGeometry().size(); s++) {
            var p = game.pieceAt(s);
            if (p != null && p.white() == game.whiteTurn()) current.put(p.id(), "" + p.type());
        }
        int ply = game.moveHistory().size();
        if (previousGame.equals(game.gameId()) && previousSide == game.whiteTurn() && ply > previousPly) {
            var lost = previousPieces.entrySet().stream().filter(e -> !current.containsKey(e.getKey()))
                    .map(Map.Entry::getValue).filter(t -> !t.equals("K")).sorted().toList();
            if (!lost.isEmpty()) {
                var recent = game.moveHistory().subList(previousPly, ply);
                String note = "Наблюдение: между моими решениями исчезли или сменили сторону мои фигуры "
                        + lost + ". Действия: " + String.join("; ", recent)
                        + ". Проверь выгодность размена, снятие защиты, открытые линии и вилки. "
                        + "Потеря сама по себе не доказывает ошибку: учитывай компенсацию в текущей позиции.";
                if (!lessons.contains(note)) {
                    lessons.add(note);
                    trim();
                    save();
                }
            }
        }
        previousGame = game.gameId();
        previousSide = game.whiteTurn();
        previousPly = ply;
        previousPieces = current;
        return List.copyOf(lessons);
    }

    private void trim() { while (lessons.size() > 30) lessons.remove(0); }

    private void save() {
        try {
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, "memory-", ".tmp");
            try {
                Files.writeString(temporary, Json.stringify(Map.of("lessons", lessons)));
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException e) {
            System.err.println("Не удалось сохранить память ChatGPT. Проверьте права на каталог data.");
        }
    }
}
