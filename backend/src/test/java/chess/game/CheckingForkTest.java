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
        assertFalse(allowed.contains("move:27:21:"),"Nxf3 ignores the checking fork");
        assertTrue(allowed.contains("move:27:33:"),"Nxb5 removes the threatening knight");
        assertEquals("move:27:33:",TestSessions.ai().choose(game));
    }
    @Test void identifiesLossAfterCheckAndIncludesRookRecapture() throws Exception {
        var game=position(66);
        var hint=TacticalHints.forPosition(game.observationForCurrentPlayer()).stream()
                .filter(h->h.actionId().equals("move:21:36:")).findFirst().orElseThrow();
        assertEquals("move:33:50:",hint.checkingForkReplyId());
        assertEquals(-180,hint.worstMaterialDeltaAfterRecapture());
    }

    @Test void actualCardEscapePreventsForcedRookLoss() throws Exception {
        var game=position(66);
        game.replaceSettings(new GameSettings(game.mode(), 4, game.difficulty()));
        RulesTest.card(game,chess.cards.CardType.ASTRAL);
        var hint=TacticalHints.forPosition(game.observationForCurrentPlayer()).stream()
                .filter(h->h.actionId().equals("move:21:36:")).findFirst().orElseThrow();
        assertEquals(0,hint.worstMaterialDeltaAfterRecapture());
    }

    @Test void frozenCheckingKnightKeepsCheckButCannotTakeRook() throws Exception {
        var game=position(66);
        game.board().set(50,null);
        game.effects().place(50,new SquareEffect(SquareEffect.Type.HIDDEN_TRAP,false,71,3));
        var hint=TacticalHints.forPosition(game.observationForCurrentPlayer()).stream()
                .filter(h->h.actionId().equals("move:21:36:")).findFirst().orElseThrow();
        assertEquals(100,hint.worstMaterialDeltaAfterRecapture());
        game.play("move:21:36:"); game.play("move:33:50:");
        assertTrue(game.inCheck(false)); assertTrue(game.pieceAt(50).frozen());
    }

}
