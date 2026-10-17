package chess.game;

import chess.ai.TacticalHints;
import chess.cards.CardType;
import chess.pieces.PieceFactory;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Позиция перед 60-м действием: Qc7xc1? Rf1xc1, ферзь за ладью без компенсации. */
class QueenSacrificeRegressionTest {
    private Game beforeAction60() {
        Game game = RulesTest.empty();
        game.board.clear();
        game.whiteTurn = false;
        String[] ranks = {"..R..RK.", "....PPBP", "......P.", "PQ.....N",
                "....n.B.", "........", ".pqppppp", "rnb..rk."};
        Set<Integer> unmoved = Set.of(12, 13, 15, 49, 51, 52, 53, 54, 55, 56, 58);
        int id = 1;
        for (int rank = 0; rank < ranks.length; rank++)
            for (int file = 0; file < 8; file++) {
                char code = ranks[rank].charAt(file);
                if (code == '.') continue;
                int square = rank * 8 + file;
                var piece = PieceFactory.create(id++, Character.toUpperCase(code), Character.isUpperCase(code));
                if (!unmoved.contains(square)) piece.markMoved();
                game.board.set(square, piece);
            }
        RulesTest.card(game, CardType.SHIELD);
        game.whiteTurn = true;
        RulesTest.card(game, CardType.REVIVE_PIECE);
        RulesTest.card(game, CardType.KING_BOOST);
        RulesTest.card(game, CardType.PAWN_BOOST);
        game.whiteTurn = false;
        return game.observationForCurrentPlayer();
    }

    @Test void filtersQueenForRookAndIdentifiesActualRecapture() {
        Game game = beforeAction60();
        var hints = TacticalHints.forPosition(game);
        var sacrifice = hints.stream().filter(h -> h.get("actionId").equals("move:50:2:")).findFirst().orElseThrow();
        assertEquals(500, sacrifice.get("immediateMaterialDelta"));
        assertEquals(-400, sacrifice.get("worstMaterialDeltaAfterOneReply"));
        assertEquals("move:5:2:", sacrifice.get("dangerousReplyId"));
        assertEquals(-400, sacrifice.get("worstMaterialDeltaAfterRecapture"));
        var safe = TacticalHints.saferActionIds(hints);
        assertFalse(safe.isEmpty());
        assertFalse(safe.contains("move:50:2:"));
        assertTrue(safe.contains("move:50:59:"), "Qd8 keeps queen rather than accepting a bad exchange");
    }
    private Game afterWhiteRecapturesQueen() {
        Game game = beforeAction60();
        var queenCapture = game.legalActions().stream().filter(a -> a.id().equals("move:50:2:")).findFirst().orElseThrow();
        game = game.simulate(queenCapture);
        var rookRecapture = game.legalActions().stream().filter(a -> a.id().equals("move:5:2:")).findFirst().orElseThrow();
        return game.simulate(rookRecapture);
    }

    @Test void action62DefendsInsteadOfPushingUndefendedPawn() {
        Game game = afterWhiteRecapturesQueen();
        var hints = TacticalHints.forPosition(game);
        var safe = TacticalHints.saferActionIds(hints);
        assertFalse(safe.contains("move:49:33:"), "b7-b5 ignores threats and offers an undefended pawn");
        assertTrue(safe.contains("move:52:44:"), "e7-e6 removes the bishop's target and opens rook's defense");
    }

    @Test void action64CannotIgnoreBishopThreatToRook() {
        Game game = afterWhiteRecapturesQueen();
        game = game.simulate(game.legalActions().stream().filter(a -> a.id().equals("move:49:33:")).findFirst().orElseThrow());
        game = game.simulate(game.legalActions().stream().filter(a -> a.id().equals("move:38:52:")).findFirst().orElseThrow());
        var hints = TacticalHints.forPosition(game);
        var pawnCapture = hints.stream().filter(h -> h.get("actionId").equals("move:33:24:")).findFirst().orElseThrow();
        assertEquals(-400, pawnCapture.get("worstMaterialDeltaAfterRecapture"));
        assertFalse(TacticalHints.saferActionIds(hints).contains("move:33:24:"), "b5xa4 leaves rook f8 to Bxf8");
    }
}
