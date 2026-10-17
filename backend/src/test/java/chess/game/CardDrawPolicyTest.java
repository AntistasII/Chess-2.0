package chess.game;

import chess.ai.CardDrawPolicy;
import chess.ai.TacticalHints;
import chess.cards.CardCatalog;
import chess.cards.CardType;
import chess.pieces.PieceFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardDrawPolicyTest {
    private Game quiet(int cards) {
        Game game = RulesTest.empty();
        game.deck.add(CardCatalog.create(2000, CardType.UNKNOWN));
        for (int i = 0; i < cards; i++) RulesTest.card(game, CardType.SHIELD);
        return game;
    }

    private boolean shouldDraw(Game game) {
        Game observation = game.observationForCurrentPlayer();
        var hints = TacticalHints.forPosition(observation);
        return CardDrawPolicy.shouldDraw(observation, hints, TacticalHints.saferActionIds(hints));
    }

    @Test void fillsSecondAndThirdSlotsInQuietPosition() {
        assertTrue(shouldDraw(quiet(1)));
        assertTrue(shouldDraw(quiet(2)));
        assertFalse(shouldDraw(quiet(3)));
    }

    @Test void neverDrawsDuringCheck() {
        Game game = quiet(1);
        game.board.set(12, PieceFactory.create(3, 'R', false));
        assertTrue(game.inCheck(true));
        assertFalse(shouldDraw(game));
    }

    @Test void savesAttackedQueenInsteadOfDrawing() {
        Game game = quiet(1);
        game.board.set(8, PieceFactory.create(3, 'Q', true));
        game.board.set(56, PieceFactory.create(4, 'R', false));
        assertFalse(shouldDraw(game));
    }

    @Test void usesReadyQueenRevivalInsteadOfDrawing() {
        Game game = quiet(0);
        RulesTest.card(game, CardType.REVIVE_PIECE);
        game.captured.add(PieceFactory.create(3, 'Q', true));
        assertFalse(shouldDraw(game));
    }

    @Test void leavesForcingChecksToOpponentDecision() {
        Game game = TestSessions.game("chatgpt", 3, "normal");
        game.play("move:12:28:");
        game.play("move:53:45:");
        assertFalse(shouldDraw(game));
    }
}
