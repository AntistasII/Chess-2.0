package chess.game;

import chess.pieces.PieceFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static chess.game.RulesTest.hasMove;

class ExpandedPawnTest {
    @Test void doubleStepWorksOnFileIForBothSidesAndShiftedStartingRanks() {
        for (boolean white : new boolean[]{true, false}) {
            for (int bottom : new int[]{0, 1}) {
              for (int left : new int[]{0, 1}) {
                var game = RulesTest.empty();
                game.expandBoard(left, 1 - left, bottom, 1);
                game.whiteTurn = white;
                var geometry = game.boardGeometry();
                int from = geometry.index(game.pawnStartRank(white), 8);
                int direction = white ? 1 : -1;
                int single = from + direction * geometry.width();
                int twice = from + 2 * direction * geometry.width();
                var pawn = PieceFactory.create(100, 'P', white);
                game.board.set(from, pawn);
                assertTrue(hasMove(game, from, single));
                assertTrue(hasMove(game, from, twice), "File i must allow the initial double step");
                pawn.markMoved();
                assertTrue(hasMove(game, from, single));
                assertFalse(hasMove(game, from, twice), "Moving already consumes the initial double step");
              }
            }
        }
    }

    @Test void doubleStepCannotJumpOverPieceOnFileI() {
        var game = RulesTest.empty();
        game.expandBoard(0, 1, 0, 0);
        var geometry = game.boardGeometry();
        int from = geometry.index(1, 8);
        game.board.set(from, PieceFactory.create(100, 'P', true));
        game.board.set(geometry.index(2, 8), PieceFactory.create(101, 'N', true));
        assertFalse(hasMove(game, from, geometry.index(3, 8)));
    }
}
