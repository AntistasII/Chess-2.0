package chess.opponent;

import chess.game.Game;
import java.nio.file.*;

public final class OpponentMemoryTest {
    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("chess-memory-");
        Path file = folder.resolve("memory.json");
        try {
            Game game = chess.game.TestSessions.game("chatgpt", 3, "normal");
            OpponentMemory memory = new OpponentMemory(file);
            assert memory.observe(game).isEmpty();
            game.play("move:12:28:");
            game.play("move:51:35:");
            game.play("move:28:35:");
            game.play("move:59:35:");
            var observed = game.observationForCurrentPlayer();
            assert observed.moveHistory().size() == 4;
            assert ChatGptOpponent.decisionInput(observed).get("history").equals(game.moveHistory());
            try { observed.moveHistory().clear(); throw new AssertionError(); }
            catch (UnsupportedOperationException expected) {}
            var lessons = memory.observe(observed);
            assert lessons.size() == 1 && lessons.get(0).contains("[P]");
            assert lessons.get(0).contains("d8 → d5");
            assert memory.observe(observed).size() == 1;
            OpponentMemory restored = new OpponentMemory(file);
            Game newGame = chess.game.TestSessions.game("chatgpt", 3, "normal");
            assert restored.observe(newGame).equals(lessons);
            assert memory.observe(newGame).size() == 1;
            assert !newGame.gameId().equals(game.gameId());
            assert game.moveHistory().size() == 4;
            System.out.println("Memory checks passed: full history, isolation, loss observation, persistence and new-game separation.");
        } finally { Files.deleteIfExists(file); Files.delete(folder); }
    }
}
