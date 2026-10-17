package chess.game;

import chess.pieces.PieceFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PieceMovementTest {
    @Test void slidingPiecesUseTheirOwnDirectionsForMovesAndAttacks() {
        for (char type : "BRQ".toCharArray()) {
            Game game = RulesTest.empty();
            game.board.set(27, PieceFactory.create(3, type, true)); // d4
            assertEquals(type != 'B', RulesTest.hasMove(game, 27, 29)); // f4
            assertEquals(type != 'R', RulesTest.hasMove(game, 27, 45)); // f6
            assertEquals(type != 'B', game.isSquareAttacked(29, true));
            assertEquals(type != 'R', game.isSquareAttacked(45, true));
            assertFalse(RulesTest.hasMove(game, 27, 44)); // e6 не лежит на луче.
            assertFalse(game.isSquareAttacked(44, true));
        }
    }

    @Test void knightJumpsOverPiecesButCannotLandOnFriendlyPiece() {
        Game game = RulesTest.empty();
        game.board.set(27, PieceFactory.create(3, 'N', true));
        game.board.set(35, PieceFactory.create(4, 'P', true));
        game.board.set(28, PieceFactory.create(5, 'P', true));
        assertTrue(RulesTest.hasMove(game, 27, 44));
        assertTrue(game.isSquareAttacked(44, true));
        game.board.set(44, PieceFactory.create(6, 'P', true));
        assertFalse(RulesTest.hasMove(game, 27, 44));
        assertTrue(game.isSquareAttacked(44, true)); // Своя фигура остаётся защищённой.
    }

    @Test void astralBlockerStopsRayAndCannotBeCaptured() {
        Game game = RulesTest.empty();
        game.board.set(27, PieceFactory.create(3, 'Q', true));
        var blocker = PieceFactory.create(4, 'N', false);
        blocker.sendToAstralUntil(10);
        game.board.set(35, blocker);
        assertFalse(RulesTest.hasMove(game, 27, 35));
        assertFalse(RulesTest.hasMove(game, 27, 43));
        assertFalse(game.isSquareAttacked(43, true));
        assertTrue(game.isSquareAttacked(35, true));
    }

    @Test void kingCannotCastleIntoOrThroughCheck() {
        for (int attackingFile : new int[]{5, 6}) {
            Game game = RulesTest.empty();
            game.board.set(0, PieceFactory.create(3, 'R', true));
            game.board.set(7, PieceFactory.create(4, 'R', true));
            game.wKingCastle = game.wQueenCastle = true;
            game.board.set(56 + attackingFile, PieceFactory.create(5, 'R', false));
            assertFalse(game.inCheck(true));
            assertTrue(game.legalActions().stream().anyMatch(a -> a.option().equals("castle") && a.to() == 2));
            assertFalse(game.legalActions().stream().anyMatch(a -> a.option().equals("castle") && a.to() == 6));
        }
    }
}
