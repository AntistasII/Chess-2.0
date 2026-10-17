package chess.game;

import chess.ai.TacticalHints;
import chess.serialization.JsonCodec;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class KnightEscapeTest {
    private Game before(int turn) throws Exception {
        try(var stream=getClass().getResourceAsStream("/ai/trapped-knight-"+turn+".json")) {
            return GameSave.decode(JsonCodec.readObject(new String(stream.readAllBytes(),StandardCharsets.UTF_8)));
        }
    }
    private boolean hasSafeExit(Game g) {
        return g.movementOptions(44).stream().anyMatch(a->!g.isSquareAttacked(a.to(),true));
    }

    @Test void opensEscapeBeforePawnAttackInsteadOfUnrelatedFlankMove() throws Exception {
        for(int turn:new int[]{34,36}) {
            var g=before(turn);
            assertFalse(hasSafeExit(g));
            var snapshot=GameSave.encode(g);
            String chosen=TestSessions.ai().choose(g);
            var action=g.legalActions().stream().filter(a->a.id().equals(chosen)).findFirst().orElseThrow();
            var next=g.simulate(action);
            assertTrue(hasSafeExit(next),"Action "+turn+" must open a route for the trapped knight: "+chosen);
            assertEquals(snapshot,GameSave.encode(g));
        }
    }

    @Test void doesNotThrowKnightAwayOnceAllRetreatsLoseMoreThanStaying() throws Exception {
        var g=before(38);
        assertTrue(g.isSquareAttacked(44,true)); assertFalse(hasSafeExit(g));
        var hints=TacticalHints.forPosition(g.observationForCurrentPlayer());
        var retreats=hints.stream().filter(h->((String)h.get("actionId")).startsWith("move:44:")).toList();
        assertEquals(4,retreats.size());
        assertTrue(retreats.stream().allMatch(h->((Number)h.get("worstMaterialDeltaAfterRecapture")).intValue()==-320));
        var wait=hints.stream().filter(h->h.get("actionId").equals("move:50:42:")).findFirst().orElseThrow();
        assertEquals(-220,((Number)wait.get("worstMaterialDeltaAfterRecapture")).intValue());
        var allowed=TacticalHints.saferActionIds(hints);
        assertTrue(retreats.stream().noneMatch(h->allowed.contains(h.get("actionId"))));
        assertTrue(allowed.contains(TestSessions.ai().choose(g)));
    }
}
