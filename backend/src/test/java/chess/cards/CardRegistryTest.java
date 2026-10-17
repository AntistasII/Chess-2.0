package chess.cards;

import chess.game.Action;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CardRegistryTest {
    private static List<Class<? extends Card>> productionClasses() {
        return Arrays.stream(CardType.values()).<Class<? extends Card>>map(t -> CardCatalog.create(0, t).getClass()).toList();
    }

    @Test void discoversClassesAndBuildsIndependentCards() {
        var registry = CardRegistry.scan("chess.cards.impl");
        var deck = registry.newDeck();
        assertEquals(44, deck.size());
        assertEquals(44, deck.stream().map(Card::id).distinct().count());
        assertTrue(deck.stream().noneMatch(c -> c.type() == CardType.UNKNOWN));
        for (CardType type : CardType.values()) {
            assertEquals(type == CardType.UNKNOWN ? 0 : 2,
                    deck.stream().filter(c -> c.type() == type).count(), type.name());
            Card first = registry.create(123, type), second = registry.create(123, type);
            assertNotSame(first, second);
            assertEquals(first, second);
            assertEquals(type, first.getClass().getDeclaredAnnotation(CardDefinition.class).type());
        }
    }

    @Test void rejectsDuplicateTypeRatherThanDependingOnScanOrder() {
        var classes = new ArrayList<>(productionClasses()); classes.add(DuplicateFreeze.class);
        assertThrows(IllegalStateException.class, () -> new CardRegistry(classes));
    }

    @Test void rejectsInvalidDefinitionsBeforeGameStarts() {
        for (Class<? extends Card> bad : List.of(NegativeCount.class, NegativeDuration.class, BadConstructor.class, MismatchedType.class, AbstractDefinition.class)) {
            var classes = new ArrayList<>(productionClasses());
            classes.removeIf(c -> c.getDeclaredAnnotation(CardDefinition.class).type() == CardType.FREEZE);
            classes.add(bad);
            assertThrows(IllegalStateException.class, () -> new CardRegistry(classes), bad.getSimpleName());
        }
    }

    @Test void durationIsReadFromAnnotationAndConvertedToHalfMoves() {
        var card = new ThreeTurnCard(1);
        assertEquals(3, card.durationTurns());
        assertEquals(18, card.deadline(12));
        assertEquals(2, CardCatalog.create(1, CardType.FREEZE).durationTurns());
        assertEquals(2, CardCatalog.create(2, CardType.ASTRAL).durationTurns());
    }

    @Test void rejectsMissingCardType() {
        var classes = new ArrayList<>(productionClasses()); classes.remove(0);
        assertThrows(IllegalStateException.class, () -> new CardRegistry(classes));
    }

    public abstract static class FixtureCard extends Card {
        protected FixtureCard(int id, CardType type) { super(id, type, "Тест"); }
        public List<Action> actions(CardContext context) { return List.of(); }
        public void apply(CardContext context, Action action) { }
    }
    @CardDefinition(type = CardType.FREEZE, copies = 3)
    public static final class DuplicateFreeze extends FixtureCard {
        public DuplicateFreeze(int id) { super(id, CardType.FREEZE); }
    }
    @CardDefinition(type = CardType.FREEZE, copies = -1)
    public static final class NegativeCount extends FixtureCard {
        public NegativeCount(int id) { super(id, CardType.FREEZE); }
    }
    @CardDefinition(type = CardType.FREEZE, copies = 3)
    public static final class BadConstructor extends FixtureCard {
        public BadConstructor() { super(0, CardType.FREEZE); }
    }
    @CardDefinition(type = CardType.FREEZE, copies = 3)
    public static final class MismatchedType extends FixtureCard {
        public MismatchedType(int id) { super(id, CardType.SHIELD); }
    }
    @CardDefinition(type = CardType.FREEZE, copies = 3)
    public abstract static class AbstractDefinition extends FixtureCard {
        public AbstractDefinition(int id) { super(id, CardType.FREEZE); }
    }
    @CardDefinition(type = CardType.FREEZE, copies = 0, durationTurns = 3)
    public static final class ThreeTurnCard extends FixtureCard {
        public ThreeTurnCard(int id) { super(id, CardType.FREEZE); }
        public int deadline(int ply) { return effectEndsAt(ply); }
    }
    @CardDefinition(type = CardType.FREEZE, copies = 0, durationTurns = -1)
    public static final class NegativeDuration extends FixtureCard {
        public NegativeDuration(int id) { super(id, CardType.FREEZE); }
    }
}
