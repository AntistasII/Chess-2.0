package chess.pieces;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PieceTest {
    @Test void copiesKeepConcreteTypeAndIndependentEffects() {
        Class<?>[] types = {Pawn.class, Knight.class, Bishop.class, Rook.class, Queen.class, King.class};
        String codes = "PNBRQK";
        for (int i = 0; i < codes.length(); i++) {
            Piece original = PieceFactory.create(17, codes.charAt(i), false);
            original.markMoved();
            original.enableBoost();
            original.protect();
            original.freezeUntil(8);
            original.sendToAstralUntil(10);
            Piece copy = original.copy();
            assertInstanceOf(types[i], copy);
            assertNotSame(original, copy);
            assertEquals(17, copy.id());
            assertEquals(codes.charAt(i), copy.type());
            assertFalse(copy.white());
            assertTrue(copy.moved());
            assertTrue(copy.boost());
            assertTrue(copy.shield());
            assertEquals(8, copy.frozenUntil());
            assertEquals(10, copy.astralUntil());
            copy.clearEffects();
            copy.changeSide(true);
            assertTrue(original.boost());
            assertTrue(original.shield());
            assertFalse(original.white());
            assertEquals(8, original.frozenUntil());
            assertEquals(10, original.astralUntil());
        }
    }

    @Test void promotionChangesClassAndKeepsIdentityAndEffects() {
        Pawn pawn = new Pawn(23, true);
        pawn.markMoved();
        pawn.protect();
        pawn.freezeUntil(9);
        for (char type : "QRBN".toCharArray()) {
            Piece promoted = pawn.promoteTo(type);
            assertEquals(type, promoted.type());
            assertFalse(promoted instanceof Pawn);
            assertEquals(23, promoted.id());
            assertTrue(promoted.white());
            assertTrue(promoted.moved());
            assertTrue(promoted.shield());
            assertEquals(9, promoted.frozenUntil());
        }
        assertEquals('P', pawn.type());
        assertThrows(IllegalArgumentException.class, () -> pawn.promoteTo('K'));
        assertThrows(IllegalArgumentException.class, () -> PieceFactory.create(1, '?', true));
    }
}
