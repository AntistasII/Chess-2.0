package chess.game;

import chess.cards.CardCatalog;
import chess.cards.CardType;
import chess.pieces.PieceFactory;
import chess.pieces.PieceType;
import chess.serialization.JsonCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static chess.game.RulesTest.card;
import static chess.game.RulesTest.cast;
import static chess.game.RulesTest.empty;
import static org.junit.jupiter.api.Assertions.*;

/** «Превращение», «Инфляция», «Шринкфляция» и вместимость руки каждого игрока. */
class HandLimitAndTransformCardsTest {
    private static boolean canDraw(Game g) {
        return g.legalActions().stream().anyMatch(Action::isDraw);
    }

    private static void apply(Game g, chess.cards.Card card) {
        g.play(g.legalActions().stream().filter(a -> a.cardId() == card.id()).findFirst().orElseThrow().id());
    }

    private static void fillDeck(Game g, int cards) {
        for (int i = 0; i < cards; i++) g.cardPiles().deck().add(CardCatalog.create(5000 + i, CardType.FREEZE));
    }

    @Test void transformSwapsOwnBishopAndKnightKeepingIdentityAndEffects() {
        Game g = empty();
        g.board().set(2, PieceFactory.create(3, 'B', true));
        g.board().set(6, PieceFactory.create(4, 'N', true));
        g.board().set(57, PieceFactory.create(5, 'B', false));
        g.board().set(0, PieceFactory.create(6, 'R', true));
        g.board().get(2).protect();
        g.board().get(2).markMoved();
        var transform = card(g, CardType.TRANSFORM);
        var targets = g.legalActions().stream().filter(a -> a.cardId() == transform.id()).map(Action::to).sorted().toList();
        assertEquals(List.of(2, 6), targets, "Только свои слоны и кони");
        cast(g, transform, 2);
        var knight = g.board().get(2);
        assertEquals(PieceType.KNIGHT, knight.type());
        assertEquals(3, knight.id());
        assertTrue(knight.white() && knight.shield() && knight.moved(), "Сторона и эффекты сохраняются");
        assertTrue(g.history().getLast().contains("Превращение"));

        g.setWhiteTurn(true);
        var second = card(g, CardType.TRANSFORM);
        cast(g, second, 6);
        assertEquals(PieceType.BISHOP, g.board().get(6).type(), "Конь становится слоном");
        assertEquals(PieceType.BISHOP, g.board().get(57).type(), "Чужие фигуры не меняются");
    }

    @Test void transformIgnoresAstralPieces() {
        Game g = empty();
        g.board().set(2, PieceFactory.create(3, 'B', true));
        g.board().get(2).sendToAstralUntil(10);
        var transform = card(g, CardType.TRANSFORM);
        assertTrue(g.legalActions().stream().noneMatch(a -> a.cardId() == transform.id()));
    }

    @Test void inflationAddsOneSlotUpToTwelve() {
        Game g = empty();
        fillDeck(g, 10);
        for (int i = 0; i < 2; i++) card(g, CardType.SHIELD);
        var inflation = card(g, CardType.INFLATION);
        assertFalse(canDraw(g), "Рука 3/3 заполнена");
        g.setHalfMoves(5);
        apply(g, inflation);
        assertEquals(5, g.halfMoves(), "Доска не меняется — счётчик для ничьей не сбрасывается");
        assertEquals(4, g.handLimit(true));
        assertEquals(3, g.handLimit(false), "Соперник не меняется");
        g.setWhiteTurn(true);
        assertTrue(canDraw(g), "2/4: добор снова доступен");

        Game max = empty();
        max.replaceSettings(new GameSettings(max.mode(), 12, max.difficulty()));
        var useless = card(max, CardType.INFLATION);
        assertTrue(max.legalActions().stream().noneMatch(a -> a.cardId() == useless.id()), "Больше 12 нельзя");
    }

    @Test void shrinkflationKeepsExtraCardsButBlocksDrawingUntilSpent() {
        Game g = empty();
        fillDeck(g, 10);
        var shrink = card(g, CardType.SHRINKFLATION);
        g.hand(false).add(CardCatalog.create(2000, CardType.SHIELD));
        g.hand(false).add(CardCatalog.create(2001, CardType.SHIELD));
        g.hand(false).add(CardCatalog.create(2002, CardType.FREEZE));
        apply(g, shrink);
        assertEquals(2, g.handLimit(false));
        assertEquals(3, g.hand(false).size(), "Карты соперника не сбрасываются: 3/2");
        assertFalse(canDraw(g));
        var view = GameView.of(g, false, true);
        assertEquals(2, view.get("handLimit"));
        assertEquals(3, view.get("enemyHandLimit"));

        // Чёрные тратят карту: 2/2 — добор всё ещё недоступен.
        g.board().set(16, PieceFactory.create(7, 'R', true)); // a3: цель заморозки, не шахует
        g.play(g.legalActions().stream().filter(a -> a.cardId() == 2002).findFirst().orElseThrow().id());
        g.setWhiteTurn(false);
        assertEquals(2, g.hand(false).size());
        assertFalse(canDraw(g), "2/2: рука заполнена, пока не увеличит вместимость");
        g.hand(false).removeFirst();
        assertTrue(canDraw(g), "1/2: добор доступен");
    }

    @Test void shrinkflationStopsAtOneSlot() {
        Game g = empty();
        g.replaceSettings(new GameSettings(g.mode(), 1, g.difficulty()));
        var shrink = card(g, CardType.SHRINKFLATION);
        assertTrue(g.legalActions().stream().noneMatch(a -> a.cardId() == shrink.id()), "Меньше 1 нельзя");
    }

    @Test void handLimitsSurviveSaveAndOldSavesUseInitialLimit() {
        Game g = empty();
        var inflation = card(g, CardType.INFLATION);
        String keyBefore = g.positionKey();
        assertFalse(keyBefore.contains("limits"), "Ключ повторения прежний, пока лимиты не менялись");
        apply(g, inflation);
        assertTrue(g.positionKey().contains("|limits:4,3"));

        var saved = GameSave.encode(g);
        Game loaded = GameSave.decode(JsonCodec.readObject(JsonCodec.toJson(saved)));
        assertEquals(4, loaded.handLimit(true));
        assertEquals(3, loaded.handLimit(false));

        // Сохранение прежней версии не содержит handLimits.
        var old = new java.util.LinkedHashMap<>(GameSave.encode(empty()));
        old.remove("handLimits");
        Game legacy = GameSave.decode(JsonCodec.readObject(JsonCodec.toJson(old)));
        assertEquals(3, legacy.handLimit(true));
        assertEquals(3, legacy.handLimit(false));

        var broken = new java.util.LinkedHashMap<>(saved);
        broken.put("handLimits", List.of(13, 3));
        assertThrows(IllegalArgumentException.class, () -> GameSave.decode(JsonCodec.readObject(JsonCodec.toJson(broken))));
    }

    @Test void searchCopiesKeepLimitsAndOpponentSeesThem() {
        Game g = empty();
        var shrink = card(g, CardType.SHRINKFLATION);
        apply(g, shrink);
        Game observation = g.observationForCurrentPlayer();
        assertEquals(2, observation.handLimit(false));
        assertEquals(3, observation.handLimit(true));
        assertEquals(2, g.copy().handLimit(false));
    }
}
