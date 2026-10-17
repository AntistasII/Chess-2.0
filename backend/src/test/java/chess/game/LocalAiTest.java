package chess.game;

import chess.cards.CardType;
import chess.pieces.PieceFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Игровые регрессии: проверяем последствия решения, а не устройство поиска. */
class LocalAiTest {
    private Action choose(Game game) {
        String id = chess.game.TestSessions.ai().choose(game);
        return game.legalActions().stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    }

    @Test void revivesQueenInsteadOfKeepingUsefulCardForever() {
        Game game = RulesTest.empty();
        game.board.set(56, PieceFactory.create(4, 'R', false));
        var revive = RulesTest.card(game, CardType.REVIVE_PIECE);
        game.captured.add(PieceFactory.create(3, 'Q', true));
        Action choice = choose(game);
        assertEquals(revive.id(), choice.cardId(), choice.id());
        assertEquals("Q", choice.option());
        assertEquals('Q', game.simulate(choice).pieceAt(3).type());
    }

    @Test void usesAstralAsOnlyEscapeFromMate() {
        Game game = RulesTest.empty();
        game.board.clear();
        game.board.set(0, PieceFactory.create(1, 'K', true));
        game.board.set(18, PieceFactory.create(2, 'K', false));
        game.board.set(9, PieceFactory.create(3, 'Q', false));
        var rescue = RulesTest.card(game, CardType.ASTRAL);
        Action choice = choose(game);
        assertEquals(rescue.id(), choice.cardId());
        assertFalse(game.simulate(choice).inCheck(true));
    }

    @Test void preventsMateThreatBeforeCheckUsingAvailableCards() {
        Game game = RulesTest.empty();
        game.board.clear();
        game.board.set(56, PieceFactory.create(1, 'K', false));
        game.board.set(42, PieceFactory.create(2, 'K', true));
        game.board.set(41, PieceFactory.create(3, 'Q', true));
        game.board.set(63, PieceFactory.create(4, 'R', false));
        game.whiteTurn = false;
        RulesTest.card(game, CardType.FREEZE);
        Game after = game.simulate(choose(game));
        for (Action reply : after.legalActions()) {
            Game next = after.simulate(reply);
            assertFalse(next.inCheck(false) && next.legalActions().isEmpty(), reply.id());
        }
    }

    @Test void choosesMateRatherThanDrawingOrSpendingCard() {
        Game game = RulesTest.empty();
        game.board.clear();
        game.board.set(56, PieceFactory.create(1, 'K', false));
        game.board.set(42, PieceFactory.create(2, 'K', true));
        game.board.set(41, PieceFactory.create(3, 'Q', true));
        RulesTest.card(game, CardType.FREEZE);
        Game next = game.simulate(choose(game));
        assertTrue(next.inCheck(false));
        assertTrue(next.legalActions().isEmpty());
    }

    @Test void doesNotSacrificeQueenForDefendedPawn() {
        Game game = RulesTest.empty();
        game.board.set(3, PieceFactory.create(3, 'Q', true));
        game.board.set(51, PieceFactory.create(4, 'P', false));
        game.board.set(59, PieceFactory.create(5, 'R', false));
        Action choice = choose(game);
        assertFalse(choice.from() == 3 && choice.to() == 51, "Qxd7 loses queen to Rd8xd7");
    }

    @Test void canDrawAnotherCardAndDoesNotMutateLivePosition() {
        Game game = RulesTest.empty();
        game.deck.add(chess.cards.CardCatalog.create(2000, CardType.CONVERT));
        RulesTest.card(game, CardType.REVIVE_PIECE); // Пока нет взятых фигур: карта не применяется.
        var before = GameSave.encode(game);
        assertEquals("draw", choose(game).kind());
        assertEquals(before, GameSave.encode(game));
    }
}
