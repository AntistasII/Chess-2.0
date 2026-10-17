package chess.game;

import chess.ai.TacticalHints;
import chess.serialization.JsonCodec;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class BishopRetreatTest {
    @Test void prefersActiveDefenceToPassiveBishopRetreat() throws Exception {
        Game game;
        try(var stream=getClass().getResourceAsStream("/ai/bishop-retreat.json")) {
            game=GameSave.decode(JsonCodec.readObject(new String(stream.readAllBytes(),StandardCharsets.UTF_8)));
        }
        var hints=TacticalHints.forPosition(game.observationForCurrentPlayer());
        var allowed=TacticalHints.saferActionIds(hints);
        var review=TestSessions.ai().analyze(game,allowed);
        assertTrue(game.isSquareAttacked(38,true), "The bishop is threatened by Nf3");
        assertFalse(game.pieceAt(38).frozen(), "Undo freeze has already expired");
        assertTrue(allowed.contains("move:38:47:"), "Bh6 is materially safe, not a blunder that loses the bishop");
        assertTrue(allowed.contains("move:38:29:"), "Bf4 remains an active safe retreat");
        assertFalse(allowed.contains("move:38:31:"), "Bh4 loses the bishop to the knight");
        assertTrue(review.depth() >= 1);
        assertTrue(review.scores().get("move:38:29:") > review.scores().get("move:38:47:"));
        assertFalse(review.competitiveActions().contains("move:38:47:"));
        assertNotEquals("move:38:47:",TestSessions.ai().choose(game));
    }
}
