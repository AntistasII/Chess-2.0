package chess.game;

import chess.cards.CardType;
import chess.pieces.PieceFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimedCardTest {
    @Test void freezeLastsTwoTargetTurnsForEitherColorAndSurvivesSaving() {
        for (boolean actorWhite : new boolean[]{true, false}) {
            Game game = RulesTest.empty();
            game.whiteTurn = actorWhite;
            int target = actorWhite ? 56 : 0;
            game.board.set(target, PieceFactory.create(3, 'R', !actorWhite));
            var freeze = RulesTest.card(game, CardType.FREEZE);
            RulesTest.cast(game, freeze, target);
            assertTrue(game.board.get(target).frozen());
            for (int step = 1; step <= 3; step++) {
                if (step == 2) game = GameSave.decode(GameSave.encode(game));
                assertTrue(game.board.get(target).frozen());
                assertFalse(RulesTest.hasMove(game, target, actorWhite ? 48 : 8));
                Game current = game;
                var kingMove = game.legalActions().stream().filter(a -> a.kind().equals("move")
                        && current.board.get(a.from()).type() == 'K').findFirst().orElseThrow();
                game.play(kingMove.id());
                assertEquals(step < 3, game.board.get(target).frozen());
            }
        }
    }
}
