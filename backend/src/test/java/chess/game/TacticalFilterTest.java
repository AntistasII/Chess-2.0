package chess.game;

import chess.ai.TacticalHints;
import chess.pieces.PieceFactory;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TacticalFilterTest {
    private Map<String, Object> hint(String id, int value) {
        return Map.of("actionId", id, "worstMaterialDeltaAfterOneReply", value, "mateInOneReplyId", "");
    }

    @Test void excludesFreePawnLossAndMissingClearMaterialWin() {
        assertEquals(Set.of("safe"), TacticalHints.saferActionIds(List.of(hint("safe", 0), hint("pawnLoss", -100))));
        assertEquals(Set.of("queenWin"), TacticalHints.saferActionIds(List.of(hint("quiet", 0), hint("queenWin", 900))));
    }

    @Test void prioritizesVerifiedMateOverMaterial() {
        var mate = Map.<String, Object>of("actionId", "mate", "mateInOne", true,
                "worstMaterialDeltaAfterOneReply", -900);
        assertEquals(Set.of("mate"), TacticalHints.saferActionIds(List.of(mate, hint("queenWin", 900))));
    }

    @Test void retainsFairExchangesAndNeverReturnsEmptyForcedLossPool() {
        var trade = Map.<String, Object>of("actionId", "trade", "worstMaterialDeltaAfterOneReply", -900,
                "worstMaterialDeltaAfterRecapture", 0);
        assertTrue(TacticalHints.saferActionIds(List.of(trade, hint("quiet", 0))).contains("trade"));
        assertEquals(Set.of("smallerLoss"), TacticalHints.saferActionIds(List.of(hint("smallerLoss", -500), hint("biggerLoss", -900))));
    }

    @Test void computesLegalRecaptureRatherThanTreatingExchangeAsQueenBlunder() {
        Game game = RulesTest.empty();
        game.whiteTurn = false;
        game.board.set(35, PieceFactory.create(3, 'Q', false)); // d5
        game.board.set(14, PieceFactory.create(4, 'B', false)); // g2
        game.board.set(21, PieceFactory.create(5, 'P', true)); // f3
        game.board.set(5, PieceFactory.create(6, 'Q', true)); // f1
        var candidate = TacticalHints.forPosition(game).stream()
                .filter(h -> h.get("actionId").equals("move:35:21:")).findFirst().orElseThrow();
        assertEquals(-800, candidate.get("worstMaterialDeltaAfterOneReply"));
        assertTrue(((Number)candidate.get("worstMaterialDeltaAfterRecapture")).intValue() >= 0);
        assertEquals('Q', game.board.get(35).type());
        assertEquals(0, game.ply);
    }
}
