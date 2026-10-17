package chess.game;

import chess.ai.TacticalHints;
import chess.cards.CardCatalog;
import chess.cards.CardType;
import chess.pieces.PieceFactory;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Реальная позиция перед 84. Na6-c7: все отступления коня уже перекрыты. */
class TrappedKnightRegressionTest {
    @Test void recognizesForcedKnightLossRatherThanInventingSafeRetreat() {
        Game game = RulesTest.empty();
        game.board.clear();
        game.whiteTurn = false;
        String[] ranks = {"......K.", "....PP.P", "......P.", "R.......",
                "........", "n.pB..pp", "......pk", "..Q....."};
        Set<Integer> unmoved = Set.of(12, 13, 15, 54);
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
        game.deck.add(CardCatalog.create(2000, CardType.UNKNOWN));
        assertTrue(game.isSquareAttacked(40, true), "Ra4 already attacks Na6");
        var hints = TacticalHints.forPosition(game);
        int best = hints.stream().mapToInt(h -> ((Number)h.get("worstMaterialDeltaAfterRecapture")).intValue()).max().orElseThrow();
        assertEquals(-320, best, "No move, card or draw saves the trapped knight");
        for (String knightMove : new String[]{"move:40:25:", "move:40:34:", "move:40:50:", "move:40:57:"}) {
            var hint = hints.stream().filter(h -> h.get("actionId").equals(knightMove)).findFirst().orElseThrow();
            assertEquals(-320, hint.get("worstMaterialDeltaAfterRecapture"), knightMove);
        }
        assertFalse(TacticalHints.saferActionIds(hints).isEmpty());
    }
}
