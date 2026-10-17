package chess.opponent;

import chess.game.TestSessions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatGptDrawTest {
    @Test void safeMandatoryRefillDoesNotDependOnModelWillingnessToDraw() {
        var opponent = new ChatGptOpponent("test-key", "test-model", "test", (key, body) -> {
            throw new AssertionError("A safe mandatory draw needs no model request");
        });
        var game = TestSessions.game("chatgpt", 3, "normal");
        assertEquals("draw", opponent.choose(game.observationForCurrentPlayer()));
        assertTrue(game.cards(true).isEmpty());
    }
    @Test void compactInputIncludesOnlyVisibleSquareEffects() {
        var game=TestSessions.game("chatgpt",3,"normal");
        var input=ChatGptOpponent.decisionInput(game);
        var field=java.util.Map.of("square",27,"type","TRAP","white",true,"turns",3,"freezeTurns",3);
        input.put("squareEffects",java.util.List.of(field));
        assertEquals(java.util.List.of(field),ChatGptOpponent.compactInput(input).get("squareEffects"));
    }

    @Test void modelReceivesActualCastlingRights() {
        var game=TestSessions.game("chatgpt",3,"normal");
        var compact=ChatGptOpponent.compactInput(ChatGptOpponent.decisionInput(game));
        var rights=(java.util.Map<?,?>)compact.get("castlingRights");
        assertEquals(java.util.List.of(true,true),rights.get("white"));
        assertEquals(java.util.List.of(true,true),rights.get("black"));
    }

}
