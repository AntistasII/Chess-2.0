package chess.game;

import chess.ai.TacticalHints;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartyReviewTest {
    @Test void rejectsRecordedMaterialBlundersForBothOpponents() {
        int[] turns = {34,54,58,60,62,64,66,74};
        String[] blunders = {"move:35:41:","move:40:32:","move:26:36:","move:50:2:",
                "move:49:33:","move:33:24:","move:36:46:","move:57:40:"};
        for (int i = 0; i < turns.length; i++) {
            var position = ReviewedParty.before(turns[i]);
            var allowed = TacticalHints.saferActionIds(TacticalHints.forPosition(position));
            assertFalse(allowed.contains(blunders[i]), "Recorded blunder at action " + turns[i]);
            assertTrue(allowed.contains(TestSessions.ai().choose(position)), "Local AI must use the same guard");
        }
    }

    @Test void searchRejectsPassiveDefenceAndLosingRookExchange() {
        int[] turns = {40,46,72,78};
        String[] inferior = {"move:42:57:","move:41:26:","move:55:47:","move:58:42:"};
        for (int i = 0; i < turns.length; i++) {
            var position = ReviewedParty.before(turns[i]);
            var allowed = TacticalHints.saferActionIds(TacticalHints.forPosition(position));
            assertTrue(allowed.contains(inferior[i]), "Position must require more than material guard");
            var review = TestSessions.ai().analyze(position, allowed);
            assertTrue(review.depth() >= 1);
            assertEquals(allowed, review.scores().keySet(), "Scores must cover a completed iteration");
            assertFalse(review.competitiveActions().contains(inferior[i]), "Inferior action " + turns[i]);
            assertTrue(review.competitiveActions().contains(review.actionId()));
        }
    }

    @Test void preservesGoodCapturesAndForcedDefencesInLostPositions() {
        int[] turns = {22,70,76,82,84,122};
        String[] valid = {"move:45:28:","move:62:63:","move:56:58:","move:63:55:","move:40:50:","move:12:5:"};
        for (int i = 0; i < turns.length; i++) {
            var position = ReviewedParty.before(turns[i]);
            var allowed = TacticalHints.saferActionIds(TacticalHints.forPosition(position));
            assertTrue(allowed.contains(valid[i]), "Fair trade or forced defence at action " + turns[i]);
            assertFalse(allowed.isEmpty());
        }
    }
}
