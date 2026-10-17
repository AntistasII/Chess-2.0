package chess.game;

import chess.ai.TacticalHints;
import chess.serialization.JsonCodec;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class CheckingForkTest {
    private Game position(int turn) throws Exception {
        try(var stream=getClass().getResourceAsStream("/ai/checking-fork-"+turn+".json")) {
            return GameSave.decode(JsonCodec.readObject(new String(stream.readAllBytes(),StandardCharsets.UTF_8)));
        }
    }
    @Test void preventsCheckingKnightForkInsteadOfIgnoringRookThreat() throws Exception {
        var game=position(64);
        var hints=TacticalHints.forPosition(game.observationForCurrentPlayer());
        var allowed=TacticalHints.saferActionIds(hints);
        System.out.println("FORK64 allowed="+allowed+" hint="+hints.stream().filter(h->h.get("actionId").equals("move:27:21:")).findFirst());
        assertFalse(allowed.contains("move:27:21:"),"Nxf3 ignores the checking fork");
        assertTrue(allowed.contains("move:27:33:"),"Nxb5 removes the threatening knight");
    }
}
