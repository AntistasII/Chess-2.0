package chess.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResignationTest {
    @Test void resignationPreservesBoardAndTurnAndSurvivesSaveRoundTrip() {
        var game = TestSessions.game("human", 3, "normal");
        var before = GameSave.encode(game);
        game.resign(false);
        assertEquals("blackResigned", game.result);
        assertEquals(1, game.version);
        assertEquals(0, game.ply);
        assertTrue(game.whiteTurn);
        assertEquals(before.get("board"), GameSave.encode(game).get("board"));
        assertTrue(game.legalActions().isEmpty());
        assertEquals("blackResigned", GameSave.decode(GameSave.encode(game)).result);
        assertThrows(IllegalArgumentException.class, () -> game.resign(true));
        assertEquals("blackResigned", game.result);
    }

    @Test void humanAlwaysResignsAsWhiteAgainstAiEvenDuringBlackTurn() {
        var game = TestSessions.game("ai", 3, "normal");
        game.play(game.legalActions().getFirst().id());
        assertFalse(game.whiteTurn);
        var session = TestSessions.create(game, g -> "draw");
        var result = session.resign(game.version, false);
        assertEquals("whiteResigned", result.get("result"));
        assertThrows(GameSession.StalePositionException.class, () -> session.resign(game.version - 1, true));
        assertThrows(IllegalArgumentException.class, () -> session.runAi(game.version));
    }

    @Test void hotSeatResignsForCurrentPlayer() {
        var game = TestSessions.game("human", 3, "normal");
        game.play(game.legalActions().getFirst().id());
        var session = TestSessions.create(game, g -> "draw");
        assertEquals("blackResigned", session.resign(game.version, true).get("result"));
    }
}
