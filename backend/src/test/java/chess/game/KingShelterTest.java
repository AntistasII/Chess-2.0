package chess.game;

import chess.ai.TacticalHints;
import chess.serialization.JsonCodec;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class KingShelterTest {
    @Test void keepsSafeKingReturnWithoutRestoringLostCastlingRights() throws Exception {
        Game game;
        try(var stream=getClass().getResourceAsStream("/ai/king-shelter.json")) {
            game=GameSave.decode(JsonCodec.readObject(new String(stream.readAllBytes(),StandardCharsets.UTF_8)));
        }
        var hints=TacticalHints.forPosition(game.observationForCurrentPlayer());
        var allowed=TacticalHints.saferActionIds(hints);
        var review=TestSessions.ai().analyze(game,allowed);
        String id="move:53:62:";
        var hint=hints.stream().filter(h->h.get("actionId").equals(id)).findFirst().orElseThrow();
        assertTrue(game.pieceAt(53).moved(), "King already lost castling on action 32");
        assertFalse(game.hasCastlingRight(false,true));
        assertFalse(game.hasCastlingRight(false,false));
        assertTrue(allowed.contains(id));
        assertEquals(0,((Number)hint.get("worstMaterialDeltaAfterOneReply")).intValue());
        assertEquals("",hint.get("mateInOneReplyId"));
        assertTrue(review.competitiveActions().contains(id),"Safe king shelter must not be forbidden as aimless walking");
        var next=game.simulate(game.legalActions().stream().filter(a->a.id().equals(id)).findFirst().orElseThrow());
        assertFalse(next.inCheck(false)); assertEquals('K',next.pieceAt(62).type());
        assertEquals('R',next.pieceAt(63).type(),"Ordinary king move leaves rook on h8");
        assertFalse(next.hasCastlingRight(false,true));
    }
}
