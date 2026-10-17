package chess.game;

import chess.ai.TacticalHints;
import chess.serialization.JsonCodec;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class KnightReturnTest {
    private Game beforeReturn() throws Exception {
        try(var stream=getClass().getResourceAsStream("/ai/knight-return.json")) {
            return GameSave.decode(JsonCodec.readObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }
    @Test void excludesUnnecessaryKnightReturnFromChatGptShortlist() throws Exception {
        var game=beforeReturn();
        var allowed=TacticalHints.saferActionIds(TacticalHints.forPosition(game.observationForCurrentPlayer()));
        var review=TestSessions.ai().analyze(game,allowed);
        assertTrue(allowed.contains("move:45:62:"), "Material guard alone misses the lost tempo");
        assertTrue(review.depth() >= 1);
        assertNotEquals("move:45:62:", TestSessions.ai().choose(game));
        assertFalse(review.competitiveActions().contains("move:45:62:"));
    }
    @Test void retreatStillAvailableWhenItIsTheOnlySafeCandidate() throws Exception {
        var game=beforeReturn();
        var review=TestSessions.ai().analyze(game,java.util.Set.of("move:45:62:"));
        assertEquals("move:45:62:",review.actionId());
        assertTrue(review.competitiveActions().contains("move:45:62:"));
    }

}
