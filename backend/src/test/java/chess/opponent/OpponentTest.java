package chess.opponent;

import chess.game.GameMode;

import chess.game.Game;
import chess.game.GameSession;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpponentTest {
    @Test void restoringGameSwitchesOpponentBackToSavedMode() {
        var session = chess.game.TestSessions.create(game -> "draw", game -> "draw");
        session.newGame("network", 3, "normal");
        var saved = session.exportGame(1);
        session.newGame("ai", 3, "normal");
        assertFalse(session.isNetwork());
        session.importGame(2, saved);
        assertTrue(session.isNetwork());
        assertThrows(IllegalArgumentException.class, () -> session.runAi(3));
    }

    @Test void selectsConcreteOpponentsBySavedMode() {
        Opponent chatGpt = new ChatGptOpponent("", "test", "test", (key, body) -> {
            throw new AssertionError("No external requests");
        });
        var local = new LocalAiOpponent(game -> "draw");
        var registry = new OpponentRegistry(local, chatGpt);
        assertSame(local, registry.forMode(GameMode.AI));
        assertSame(chatGpt, registry.forMode(GameMode.CHATGPT));
        assertInstanceOf(NetworkOpponent.class, registry.forMode(GameMode.NETWORK));
        assertInstanceOf(HumanOpponent.class, registry.forMode(GameMode.HUMAN));
        assertTrue(local.automatic());
        assertFalse(local.requiresScoutPreview());
        assertTrue(chatGpt.automatic());
        assertTrue(chatGpt.requiresScoutPreview());
        assertEquals("draw", local.choose(chess.game.TestSessions.game("ai", 3, "normal")));
        assertThrows(IllegalArgumentException.class, () -> GameMode.fromId("missing"));
    }

    @Test void manualOpponentsNeverGenerateAutomaticMoves() {
        Opponent network = new NetworkOpponent();
        Opponent human = new HumanOpponent();
        assertTrue(network.network());
        assertFalse(human.network());
        for (Opponent opponent : new Opponent[]{network, human}) {
            assertFalse(opponent.automatic());
            assertFalse(opponent.requiresScoutPreview());
            assertThrows(IllegalArgumentException.class,
                    () -> opponent.choose(chess.game.TestSessions.game(opponent.mode().id(), 3, "normal")));
        }
    }
}
