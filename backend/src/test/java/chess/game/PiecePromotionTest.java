package chess.game;

import chess.pieces.Pawn;
import chess.pieces.Queen;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PiecePromotionTest {
    @Test void promotionReplacesPawnOnBoardAndConsumesMoveEffects() {
        Game game = RulesTest.empty();
        Pawn pawn = new Pawn(3, true);
        pawn.enableBoost();
        pawn.protect();
        game.board.set(48, pawn);
        var promotion = game.legalActions().stream()
                .filter(a -> a.from() == 48 && a.to() == 56 && a.option().equals("Q"))
                .findFirst().orElseThrow();
        game.play(promotion.id());
        var queen = assertInstanceOf(Queen.class, game.board.get(56));
        assertEquals(3, queen.id());
        assertTrue(queen.moved());
        assertFalse(queen.boost());
        assertFalse(queen.shield());
        assertNull(game.board.get(48));
        assertInstanceOf(Queen.class, game.copy().board.get(56));
    }
}
