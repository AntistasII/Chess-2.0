package chess.game;

import chess.ai.TacticalHints;
import chess.serialization.JsonCodec;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class CastlingChoiceTest {
    private Game position() throws Exception {
        try (var stream=getClass().getResourceAsStream("/ai/king-castling.json")) {
            return GameSave.decode(JsonCodec.readObject(new String(stream.readAllBytes(),StandardCharsets.UTF_8)));
        }
    }
    @Test void avoidsGivingUpCastlingForUnnecessaryKingWalk() throws Exception {
        var g=position();
        assertFalse(g.inCheck(false));
        assertTrue(g.legalActions().stream().anyMatch(a->a.id().equals("move:60:62:castle")));
        var allowed=TacticalHints.saferActionIds(TacticalHints.forPosition(g.observationForCurrentPlayer()));
        var review=TestSessions.ai().analyze(g,allowed);
        assertTrue(review.depth() >= 1);
        assertTrue(allowed.contains("move:60:53:"), "Material guard alone does not preserve castling");
        assertFalse(review.competitiveActions().contains("move:60:53:"));
        assertNotEquals("move:60:53:",TestSessions.ai().choose(g));
    }
    @Test void kingMovePermanentlyLosesRightsButCastlingRemainsLegalBeforeIt() throws Exception {
        var g=position();
        assertTrue(g.hasCastlingRight(false,true)); assertTrue(g.hasCastlingRight(false,false));
        Game castled=g.simulate(g.legalActions().stream().filter(a->a.id().equals("move:60:62:castle")).findFirst().orElseThrow());
        assertEquals('K',castled.board.get(62).type()); assertEquals('R',castled.board.get(61).type());
        assertFalse(castled.hasCastlingRight(false,true));
        var kingWalk=g.simulate(g.legalActions().stream().filter(a->a.id().equals("move:60:53:")).findFirst().orElseThrow());
        assertFalse(kingWalk.hasCastlingRight(false,true)); assertFalse(kingWalk.hasCastlingRight(false,false));
        kingWalk.board.set(60,kingWalk.board.get(53)); kingWalk.board.set(53,null);
        assertFalse(kingWalk.hasCastlingRight(false,true),"Returning king does not restore rights");
        assertTrue(g.hasCastlingRight(false,true),"Search cannot alter live rights");
    }

    @Test void forcedKingDefenceAndEndgameKingMovesAreStillAvailable() throws Exception {
        var g=position();
        g.board.set(59,null);
        g.board.set(52,chess.pieces.PieceFactory.create(500,'R',true));
        assertTrue(g.inCheck(false));
        assertTrue(g.legalActions().stream().anyMatch(a->a.id().equals("move:60:59:")));
        assertEquals("move:60:59:",TestSessions.ai().analyze(g,java.util.Set.of("move:60:59:")).actionId());
        g=RulesTest.empty();
        assertTrue(g.legalActions().stream().anyMatch(a->a.id().equals("move:4:12:")));
        assertEquals("move:4:12:",TestSessions.ai().analyze(g,java.util.Set.of("move:4:12:")).actionId());
    }

    @Test void castlingRightsFollowOriginalSquaresAfterExpansion() throws Exception {
        var g=position();
        g.expandBoard(1,1,2,1);
        assertTrue(g.hasCastlingRight(false,true)); assertTrue(g.hasCastlingRight(false,false));
        int king=g.boardGeometry().originalSquare(7,4);
        assertTrue(g.legalActions().stream().anyMatch(a->a.from()==king && a.option().equals("castle")));
    }

}
