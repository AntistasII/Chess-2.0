package chess.game;

import chess.pieces.PieceFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameLifecycleTest {
    private void cycle(Game game) {
        RulesTest.move(game, 1, 18);
        RulesTest.move(game, 57, 42);
        RulesTest.move(game, 18, 1);
        RulesTest.move(game, 42, 57);
    }

    @Test void repetitionsSurviveSaveAndContinueToAutomaticDraw() {
        Game game = RulesTest.empty();
        game.board().set(1, PieceFactory.create(3, 'N', true));
        game.board().set(57, PieceFactory.create(4, 'N', false));
        cycle(game);
        cycle(game);
        assertFalse(game.canClaimDraw());
        Game restored = GameSave.decode(GameSave.encode(game));
        cycle(restored);
        assertTrue(restored.canClaimDraw());
        assertTrue(restored.result() == GameResult.IN_PROGRESS);
        cycle(restored);
        // Первой в пятый раз повторится промежуточная позиция с двумя вышедшими конями.
        RulesTest.move(restored, 1, 18);
        RulesTest.move(restored, 57, 42);
        assertEquals("repetition", restored.result().id());
        assertTrue(restored.legalActions().isEmpty());
        assertTrue(game.result() == GameResult.IN_PROGRESS);
    }

    @Test void mateHasPriorityOverSeventyFiveMoveDraw() {
        Game game = RulesTest.empty();
        game.board().clear();
        game.board().set(56, PieceFactory.create(1, 'K', false));
        game.board().set(42, PieceFactory.create(2, 'K', true));
        game.board().set(41, PieceFactory.create(3, 'Q', true));
        game.setHalfMoves(149);
        RulesTest.move(game, 41, 49);
        assertEquals(150, game.halfMoves());
        assertEquals("white", game.result().id());
    }

    @Test void automaticDrawEndsQuietGameAndHistoryNamesPieces() {
        Game game = RulesTest.empty();
        game.setHalfMoves(149);
        RulesTest.move(game, 4, 3);
        assertEquals("seventyFiveMoves", game.result().id());
        assertEquals("Белые: Король e1 → d1", game.moveHistory().getFirst());
        assertEquals(1, game.ply());
        assertEquals(1, game.version());
        assertFalse(game.whiteTurn());
    }
}
