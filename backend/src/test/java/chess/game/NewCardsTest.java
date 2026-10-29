package chess.game;

import chess.cards.*;
import chess.pieces.PieceFactory;
import chess.serialization.JsonCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static chess.game.RulesTest.*;
import static org.junit.jupiter.api.Assertions.*;

class NewCardsTest {
    private Game game() { Game g = empty(); g.nextPieceId = 100; return g; }
    private Action action(Game g, Card c) { return g.legalActions().stream().filter(a -> a.cardId() == c.id()).findFirst().orElseThrow(); }
    private void waitTurn(Game g) {
        var king = g.legalActions().stream().filter(a -> a.kind().equals("move") && g.board.get(a.from()).type() == 'K').findFirst().orElseThrow();
        g.play(king.id());
    }
    private Game load(Game g) { return GameSave.decode(JsonCodec.readObject(JsonCodec.toJson(GameSave.encode(g)))); }

    @Test void shieldCannotTargetKingButCanProtectOwnPawn() {
        Game g = game();
        Card shield = card(g, CardType.SHIELD);
        g.board.set(8, PieceFactory.create(3, 'P', true));
        var actions = g.legalActions().stream().filter(a -> a.cardId() == shield.id()).toList();
        assertTrue(actions.stream().noneMatch(a -> a.to() == 4 || a.to() == 60));
        assertTrue(actions.stream().anyMatch(a -> a.to() == 8));
    }

    @Test void undoRestoresCaptureAndFreezesMoverForOneOwnerAction() {
        Game g = game();
        g.board.set(0, PieceFactory.create(3, 'R', true));
        g.board.set(8, PieceFactory.create(4, 'R', false));
        g.whiteTurn = false; Card undo = card(g, CardType.UNDO_MOVE); g.whiteTurn = true;
        move(g, 0, 8);
        assertEquals(1, g.captured.size());
        cast(g, undo, 8);
        assertEquals(3, g.board.get(0).id()); assertEquals(4, g.board.get(8).id());
        assertTrue(g.captured.isEmpty()); assertTrue(g.board.get(0).frozen());
        assertFalse(hasMove(g, 0, 1));
        waitTurn(g);
        assertFalse(g.board.get(0).frozen());
        assertEquals(3, g.ply); assertEquals(3, g.history.size());
        assertTrue(g.discard.contains(undo));
    }

    @Test void undoRestoresCastlingRightsAndBothPiecesAfterSave() {
        Game g = game(); g.board.set(7, PieceFactory.create(3, 'R', true)); g.wKingCastle = true;
        g.whiteTurn = false; Card undo = card(g, CardType.UNDO_MOVE); g.whiteTurn = true;
        g.play("move:4:6:castle");
        Game loaded = load(g);
        cast(loaded, undo, 6);
        assertEquals('K', loaded.board.get(4).type()); assertEquals('R', loaded.board.get(7).type());
        assertNull(loaded.board.get(5)); assertNull(loaded.board.get(6));
        assertTrue(loaded.wKingCastle); assertFalse(loaded.board.get(4).moved());
        assertTrue(loaded.board.get(4).frozen());
        assertFalse(g.board.get(6).frozen());
    }

    @Test void undoRestoresEnPassantVictimAndPromotionPawn() {
        Game g = game(); g.board.set(36, PieceFactory.create(3, 'P', true)); g.board.set(35, PieceFactory.create(4, 'P', false));
        g.enPassant = 43;
        g.whiteTurn = false; Card undo = card(g, CardType.UNDO_MOVE); g.whiteTurn = true;
        move(g, 36, 43); cast(g, undo, 43);
        assertEquals('P', g.board.get(36).type()); assertEquals('P', g.board.get(35).type());
        assertNull(g.board.get(43)); assertEquals(43, g.enPassant);
        g = game(); g.board.set(48, PieceFactory.create(3, 'P', true));
        g.whiteTurn = false; undo = card(g, CardType.UNDO_MOVE); g.whiteTurn = true;
        g.play("move:48:56:Q"); cast(g, undo, 56);
        assertEquals('P', g.board.get(48).type()); assertTrue(g.board.get(48).frozen()); assertNull(g.board.get(56));
    }

    @Test void undoCannotUndoCardDrawOrOlderMove() {
        Game g = game(); Card undo = card(g, CardType.UNDO_MOVE);
        assertTrue(g.legalActions().stream().noneMatch(a -> a.cardId() == undo.id()));
        g.whiteTurn = false; g.deck.add(CardCatalog.create(400, CardType.SHIELD)); g.play("draw");
        assertTrue(g.legalActions().stream().noneMatch(a -> a.cardId() == undo.id()));
        Game old = game(); var save = new HashMap<>(GameSave.encode(old)); save.remove("lastMove"); save.remove("squareEffects");
        assertTrue(GameSave.decode(save).squareEffects.isEmpty());
    }

    @Test void allFourExpansionCardsPreservePiecesEffectsAndOriginalSquares() {
        for (CardType type : List.of(CardType.EXPAND_BOTTOM, CardType.EXPAND_TOP, CardType.EXPAND_LEFT, CardType.EXPAND_RIGHT)) {
            Game g = game(); g.board.set(8, PieceFactory.create(3, 'P', true)); g.board.get(8).protect();
            g.squareEffects.put(27, new SquareEffect(SquareEffect.Type.HIDDEN_TRAP, true, 20, 3));
            Card c = card(g, type); g.play(action(g,c).id());
            var geo = g.boardGeometry();
            assertEquals(type == CardType.EXPAND_LEFT || type == CardType.EXPAND_RIGHT ? 9 : 8, geo.width());
            assertEquals(type == CardType.EXPAND_BOTTOM || type == CardType.EXPAND_TOP ? 9 : 8, geo.height());
            assertEquals(3, g.board.get(geo.originalSquare(1,0)).id());
            assertTrue(g.board.get(geo.originalSquare(1,0)).shield());
            assertTrue(g.squareEffects.containsKey(geo.originalSquare(3,3)));
            assertEquals(1, g.ply); assertEquals(1, g.version); assertEquals(1, g.history.size());
            assertEquals(GameSave.encode(g).get("board"), GameSave.encode(load(g)).get("board"));
        }
    }

    @Test void expansionDoesNotTriggerExistingTrapOrChangePieceIdentity() {
        Game g = game(); g.board.set(27, PieceFactory.create(3, 'N', true));
        g.squareEffects.put(27, new SquareEffect(SquareEffect.Type.TRAP, true, 20, 3));
        Card c = card(g, CardType.EXPAND_LEFT); g.play(action(g,c).id());
        assertFalse(g.board.get(g.boardGeometry().originalSquare(3,3)).frozen());
    }

    @Test void destroyRemovesExactlyOneEnemyCardPermanentlyAndNeverRevealsIt() {
        Game g = game(); Card destroy = card(g, CardType.DESTROY_CARD);
        g.blackHand.add(CardCatalog.create(400, CardType.SHIELD)); g.blackHand.add(CardCatalog.create(401, CardType.ASTRAL));
        Set<Card> before = Set.copyOf(g.blackHand); g.play(action(g,destroy).id());
        assertEquals(1, g.blackHand.size());
        var removed = before.stream().filter(c -> !g.blackHand.contains(c)).findFirst().orElseThrow();
        assertFalse(g.deck.contains(removed)); assertFalse(g.discard.contains(removed));
        assertEquals(List.of(destroy), g.discard);
        assertFalse(g.history.getLast().contains(removed.name()));
        Game empty = game(); Card unusable = card(empty, CardType.DESTROY_CARD);
        assertTrue(empty.legalActions().stream().noneMatch(a -> a.cardId() == unusable.id()));
    }

    @Test void sacrificeHasTwoTargetsAndCannotRemovePawnKingEnemyOrExposeKing() {
        Game g = game(); g.board.set(0, PieceFactory.create(3, 'R', true)); g.wQueenCastle = true;
        g.board.set(9, PieceFactory.create(4, 'P', true)); g.board.set(57, PieceFactory.create(5, 'N', false));
        Card c = card(g, CardType.SACRIFICE);
        var candidates = g.legalActions().stream().filter(a -> a.cardId() == c.id()).toList();
        assertEquals(7, candidates.size()); assertTrue(candidates.stream().allMatch(a -> a.to() == 0 && a.other() >= 8 && a.other() < 16 && a.other() != 9));
        g.play(candidates.stream().filter(a -> a.other() == 8).findFirst().orElseThrow().id());
        assertNull(g.board.get(0)); assertEquals('P', g.board.get(8).type()); assertTrue(g.captured.isEmpty()); assertFalse(g.wQueenCastle);
        Game pinned = game(); pinned.board.set(12, PieceFactory.create(3, 'N', true)); pinned.board.set(52, PieceFactory.create(4, 'R', false));
        Card sacrifice = card(pinned, CardType.SACRIFICE);
        var rescues = pinned.legalActions().stream().filter(a -> a.cardId() == sacrifice.id()).toList();
        assertEquals(1, rescues.size());
        assertEquals(12, rescues.getFirst().other(), "Replacing the pinned knight with a pawn still blocks check");
        pinned.play(rescues.getFirst().id());
        assertEquals('P', pinned.board.get(12).type()); assertFalse(pinned.inCheck(true));
    }

    @Test void trapExpiresAfterThreeRoundsButEntryFreezeLastsThreeFutureOwnerActions() {
        Game g = game(); g.board.set(42, PieceFactory.create(3, 'N', false));
        Card trap = card(g, CardType.TRAP); cast(g, trap, 27); move(g,42,27);
        assertTrue(g.board.get(27).frozen()); assertEquals(8, g.board.get(27).frozenUntil());
        for (int ply = 2; ply < 8; ply++) {
            if (!g.whiteTurn) assertTrue(g.legalActions().stream().noneMatch(a -> a.from() == 27));
            waitTurn(g);
            if (g.ply == 6) assertTrue(g.squareEffects.isEmpty());
        }
        assertFalse(g.board.get(27).frozen());
        assertTrue(load(g).squareEffects.isEmpty());
    }

    @Test void hiddenTrapIsHiddenFromStateHistoryObservationAndPlacementActions() {
        Game g = game(); Card hidden = card(g, CardType.HIDDEN_TRAP); cast(g, hidden,27);
        assertEquals(1, ((List<?>)GameView.of(g,true,false).get("squareEffects")).size());
        assertTrue(((List<?>)GameView.of(g,false,true).get("squareEffects")).isEmpty());
        assertFalse(g.history.getLast().contains("d4"));
        assertTrue(g.observationForCurrentPlayer().squareEffects().isEmpty());
        Card visible = card(g,CardType.TRAP);
        assertTrue(g.legalActions().stream().anyMatch(a -> a.cardId() == visible.id() && a.to() == 27), "Action list must not reveal hidden trap");
        Game loaded = load(g);
        assertEquals(g.squareEffects,loaded.squareEffects);
        assertTrue(((List<?>)GameView.of(loaded,false,true).get("squareEffects")).isEmpty());
    }

    @Test void whiteHoleBlocksRaysPawnPathsCastlingRevivalAndSacrificeButKnightCanJump() {
        Game g = game(); g.board.set(0, PieceFactory.create(3,'R',true)); g.board.set(1, PieceFactory.create(4,'N',true));
        g.board.set(8, PieceFactory.create(5,'P',true));
        g.squareEffects.put(16,new SquareEffect(SquareEffect.Type.WHITE_HOLE,false,6,0));
        assertFalse(hasMove(g,0,16)); assertFalse(hasMove(g,0,24)); assertFalse(g.attacked(24,true));
        assertFalse(hasMove(g,8,16)); assertFalse(hasMove(g,8,24)); assertTrue(hasMove(g,1,18));
        g.squareEffects.put(18,new SquareEffect(SquareEffect.Type.WHITE_HOLE,false,6,0)); assertFalse(hasMove(g,1,18));
        g.squareEffects.put(5,new SquareEffect(SquareEffect.Type.WHITE_HOLE,false,6,0));
        g.board.set(7, PieceFactory.create(6,'R',true)); g.wKingCastle = true;
        assertTrue(g.legalActions().stream().noneMatch(a -> a.option().equals("castle")));
        g.squareEffects.put(9,new SquareEffect(SquareEffect.Type.WHITE_HOLE,false,6,0));
        g.captured.add(PieceFactory.create(7,'P',true)); Card revive = card(g,CardType.REVIVE_PAWN); Card sacrifice = card(g,CardType.SACRIFICE);
        assertTrue(g.legalActions().stream().noneMatch(a -> a.cardId()==revive.id() && a.to()==9 || a.cardId()==sacrifice.id() && a.other()==9));
    }

    @Test void whiteHoleCannotBlockCheckOrBePlayedAsCheckDefence() {
        Game g = game(); g.board.set(52, PieceFactory.create(3,'R',false));
        assertTrue(g.inCheck(true)); Card hole = card(g,CardType.WHITE_HOLE);
        assertTrue(g.legalActions().stream().noneMatch(a -> a.cardId() == hole.id()));
        g.squareEffects.put(28, new SquareEffect(SquareEffect.Type.WHITE_HOLE, true, 6, 0));
        assertTrue(g.inCheck(true), "A white hole between king and rook must not hide check");
        assertTrue(load(g).inCheck(true), "Loading preserves the same attack rules");
        assertTrue(g.legalActions().stream().noneMatch(a -> a.kind().equals("draw")));
        assertTrue(g.legalActions().stream().allMatch(a -> !g.simulate(a).inCheck(true)));
    }

    @Test void whiteHoleDoesNotShieldRookBishopOrQueenRaysButPiecesStillDo() {
        for (var setup : List.of(new int[]{'R',52,28,20}, new int[]{'B',32,18,11},
                new int[]{'Q',52,28,20}, new int[]{'Q',32,18,11})) {
            Game g = game();
            g.board.set(setup[1], PieceFactory.create(3, (char)setup[0], false));
            g.squareEffects.put(setup[2], new SquareEffect(SquareEffect.Type.WHITE_HOLE, false, 6, 0));
            assertTrue(g.inCheck(true), "Check passes through the white hole: " + (char)setup[0]);
            g.board.set(setup[3], PieceFactory.create(4, 'N', true));
            assertFalse(g.inCheck(true), "An actual piece still blocks the ray");
        }
    }

    @Test void castlingCannotPassThroughCheckHiddenBehindWhiteHole() {
        Game g = game();
        g.board.set(7, PieceFactory.create(3, 'R', true)); g.wKingCastle = true;
        g.board.set(61, PieceFactory.create(4, 'R', false));
        g.squareEffects.put(29, new SquareEffect(SquareEffect.Type.WHITE_HOLE, false, 6, 0));
        assertFalse(g.inCheck(true));
        assertTrue(g.attacked(5, false), "f1 is attacked through the hole on f4");
        assertTrue(g.legalActions().stream().noneMatch(a -> a.option().equals("castle")));
    }
    @Test void trapsTriggerOnSwapRevivalSacrificeAndKingEntryIncludingShield() {
        Game g = game(); g.board.set(0,PieceFactory.create(3,'R',true));
        g.board.set(8,PieceFactory.create(4,'N',true)); g.board.get(8).protect();
        g.squareEffects.put(0,new SquareEffect(SquareEffect.Type.TRAP,false,20,3));
        Card swap=card(g,CardType.SWAP); cast(g,swap,0);
        assertEquals('N',g.board.get(0).type()); assertTrue(g.board.get(0).frozen());
        assertFalse(g.board.get(8).frozen());
        g=game(); g.captured.add(PieceFactory.create(3,'P',true));
        g.squareEffects.put(8,new SquareEffect(SquareEffect.Type.TRAP,false,20,3));
        Card revive=card(g,CardType.REVIVE_PAWN); cast(g,revive,8); assertTrue(g.board.get(8).frozen());
        g=game(); g.board.set(0,PieceFactory.create(3,'N',true));
        g.squareEffects.put(8,new SquareEffect(SquareEffect.Type.TRAP,false,20,3));
        Card sacrifice=card(g,CardType.SACRIFICE);
        g.play(g.legalActions().stream().filter(a->a.cardId()==sacrifice.id() && a.to()==0 && a.other()==8).findFirst().orElseThrow().id());
        assertTrue(g.board.get(8).frozen());
        g=game(); g.board.get(4).protect(); g.squareEffects.put(3,new SquareEffect(SquareEffect.Type.TRAP,false,20,3));
        move(g,4,3); assertTrue(g.board.get(3).frozen()); assertEquals('K',g.board.get(3).type());
    }

    @Test void networkViewsKeepFixedOwnerAndHideOnlyEnemySecretFields() {
        Game g=game(); g.mode="network";
        g.squareEffects.put(27,new SquareEffect(SquareEffect.Type.HIDDEN_TRAP,true,20,3));
        g.squareEffects.put(29,new SquareEffect(SquareEffect.Type.HIDDEN_TRAP,false,20,3));
        g.squareEffects.put(28,new SquareEffect(SquareEffect.Type.WHITE_HOLE,false,20,0));
        var session=TestSessions.create(position -> {throw new AssertionError("Network has no AI");});
        long version=((Number)session.state().get("version")).longValue();
        session.importGame(version,GameSave.encode(g));
        for (boolean white : List.of(true,false)) {
            var state=session.playerState(white);
            assertEquals(white,state.get("handWhite")); assertEquals(white,state.get("playerWhite"));
            var fields=(List<?>)state.get("squareEffects"); assertEquals(2,fields.size());
            assertTrue(fields.stream().map(o->(Map<?,?>)o).anyMatch(e->e.get("square").equals(white?27:29)));
            assertFalse(fields.stream().map(o->(Map<?,?>)o).anyMatch(e->e.get("square").equals(white?29:27)));
        }
    }

    @Test void localAiCanAnalyzeNewCardsAndFieldsOnExpandedBoardWithoutMutatingGame() {
        for (CardType type : List.of(CardType.UNDO_MOVE,CardType.TRAP,CardType.HIDDEN_TRAP,CardType.WHITE_HOLE,
                CardType.EXPAND_LEFT,CardType.EXPAND_TOP,CardType.DESTROY_CARD,CardType.SACRIFICE)) {
            Game g=game(); g.difficulty="easy"; g.expandBoard(1,0,1,0);
            var geo=g.boardGeometry(); g.board.set(geo.originalSquare(0,1),PieceFactory.create(3,'N',true));
            g.blackHand.add(CardCatalog.create(400,CardType.SHIELD));
            g.squareEffects.put(geo.originalSquare(3,3),new SquareEffect(SquareEffect.Type.WHITE_HOLE,false,20,0));
            g.squareEffects.put(geo.originalSquare(4,4),new SquareEffect(SquareEffect.Type.HIDDEN_TRAP,false,20,3));
            g.whiteTurn=false; move(g,geo.originalSquare(7,4),geo.originalSquare(7,5));
            card(g,type);
            var before=GameSave.encode(g);
            String choice=TestSessions.ai().choose(g);
            assertTrue(g.legalActions().stream().anyMatch(a->a.id().equals(choice)),type.toString());
            assertEquals(before,GameSave.encode(g),"AI must not consume cards or change fields");
        }
    }

    @Test void adjacentWhiteHoleDoesNotRemoveTrapBeforeItsOwnDeadline() {
        Game g=game();
        waitTurn(g); waitTurn(g);
        Card trap=card(g,CardType.TRAP); cast(g,trap,35); // Действие 3: d5, окончание на ply=8.
        waitTurn(g); waitTurn(g); waitTurn(g);
        Card hole=card(g,CardType.WHITE_HOLE); cast(g,hole,36); // Действие 7: соседняя e5.
        assertEquals(7,g.ply);
        assertEquals(SquareEffect.Type.TRAP,g.squareEffects.get(35).type());
        assertEquals(8,g.squareEffects.get(35).expiresAt());
        assertEquals(SquareEffect.Type.WHITE_HOLE,g.squareEffects.get(36).type());
        assertEquals(12,g.squareEffects.get(36).expiresAt());
        assertEquals(2,((List<?>)GameView.of(g,true,false).get("squareEffects")).size());
        Game restored=load(g);
        waitTurn(restored); // Действие 8: третий ответ соперника после капкана.
        assertFalse(restored.squareEffects.containsKey(35));
        assertTrue(restored.squareEffects.containsKey(36));
        assertEquals(2,g.squareEffects.size(),"Expiry on a loaded copy must not change the original");
        for(int ply=8;ply<12;ply++) waitTurn(restored);
        assertTrue(restored.squareEffects.isEmpty());
    }

}
