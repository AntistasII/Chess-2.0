package chess.game;

import chess.ai.TacticalHint;
import chess.ai.TacticalHints;
import chess.pieces.PieceFactory;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TacticalFilterTest {
    private TacticalHint hint(String id, int value) {
        return TacticalHint.material(id, value);
    }

    @Test void excludesFreePawnLossAndMissingClearMaterialWin() {
        assertEquals(Set.of("safe"), TacticalHints.saferActionIds(List.of(hint("safe", 0), hint("pawnLoss", -100))));
        assertEquals(Set.of("queenWin"), TacticalHints.saferActionIds(List.of(hint("quiet", 0), hint("queenWin", 900))));
    }

    @Test void prioritizesVerifiedMateOverMaterial() {
        var mate = new TacticalHint("mate", 0, -900, "", "", true, false, -900, "");
        assertEquals(Set.of("mate"), TacticalHints.saferActionIds(List.of(mate, hint("queenWin", 900))));
    }

    @Test void retainsFairExchangesAndNeverReturnsEmptyForcedLossPool() {
        var trade = new TacticalHint("trade", 0, -900, "", "", false, false, 0, "");
        assertTrue(TacticalHints.saferActionIds(List.of(trade, hint("quiet", 0))).contains("trade"));
        assertEquals(Set.of("smallerLoss"), TacticalHints.saferActionIds(List.of(hint("smallerLoss", -500), hint("biggerLoss", -900))));
    }

    @Test void computesLegalRecaptureRatherThanTreatingExchangeAsQueenBlunder() {
        Game game = RulesTest.empty();
        game.setWhiteTurn(false);
        game.board().set(35, PieceFactory.create(3, 'Q', false)); // d5
        game.board().set(14, PieceFactory.create(4, 'B', false)); // g2
        game.board().set(21, PieceFactory.create(5, 'P', true)); // f3
        game.board().set(5, PieceFactory.create(6, 'Q', true)); // f1
        var candidate = TacticalHints.forPosition(game).stream()
                .filter(h -> h.actionId().equals("move:35:21:")).findFirst().orElseThrow();
        assertEquals(-800, candidate.worstMaterialDeltaAfterOneReply());
        assertTrue(candidate.worstMaterialDeltaAfterRecapture() >= 0);
        assertEquals('Q', game.board().get(35).type().code());
        assertEquals(0, game.ply());
    }
}
