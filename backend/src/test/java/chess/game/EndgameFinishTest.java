package chess.game;

import chess.pieces.PieceFactory;
import chess.ai.TacticalHints;
import chess.ai.CardDrawPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EndgameFinishTest {
    public static Game mateInTwo() {
        var game=RulesTest.empty(); game.board.clear(); game.nextPieceId=100;
        game.board.set(12,PieceFactory.create(1,'K',true));  // e2
        game.board.set(6,PieceFactory.create(2,'K',false)); // g1
        game.board.set(26,PieceFactory.create(3,'Q',true)); // c4
        game.board.set(58,PieceFactory.create(4,'R',true)); // c8
        return game;
    }

    @Test void findsCheckingMateInTwoAndVerifiesEveryKingEscape() {
        var game=mateInTwo();
        var hints=TacticalHints.forPosition(game);
        assertFalse(hints.stream().anyMatch(h->Boolean.TRUE.equals(h.get("mateThreat"))),"No mate in one");
        var review=TestSessions.ai().analyze(game,TacticalHints.saferActionIds(hints));
        assertEquals(3,review.matePlies());
        assertEquals(0,review.depth(),"A forced line is not a completed full root iteration");
        var action=game.legalActions().stream().filter(a->a.id().equals(review.actionId())).findFirst().orElseThrow();
        var checked=game.simulate(action);
        assertTrue(checked.inCheck(false));
        assertFalse(checked.legalActions().isEmpty());
        for(var defence:checked.legalActions()) {
            var position=checked.simulate(defence);
            assertTrue(position.legalActions().stream().anyMatch(a->{
                var end=position.simulate(a);
                return end.inCheck(false) && end.legalActions().isEmpty();
            }),defence.id());
        }
    }

    @Test void doesNotDrawInsteadOfClosingMatingNetAndDoesNotMutateGame() {
        var game=mateInTwo();
        game.deck.add(chess.cards.CardCatalog.create(500,chess.cards.CardType.SCOUT));
        var snapshot=GameSave.encode(game);
        var hints=TacticalHints.forPosition(game);
        assertFalse(CardDrawPolicy.shouldDraw(game,hints,TacticalHints.saferActionIds(hints)));
        assertNotEquals("draw",TestSessions.ai().choose(game));
        assertEquals(snapshot,GameSave.encode(game));
    }

    @Test void avoidsStalemateWhenWinning() {
        var game=RulesTest.empty(); game.board.clear();
        game.board.set(42,PieceFactory.create(1,'K',true));
        game.board.set(56,PieceFactory.create(2,'K',false));
        game.board.set(34,PieceFactory.create(3,'Q',true));
        var stale=game.simulate(game.legalActions().stream().filter(a->a.id().equals("move:34:41:")).findFirst().orElseThrow());
        assertFalse(stale.inCheck(false)); assertTrue(stale.legalActions().isEmpty());
        String chosen=TestSessions.ai().choose(game);
        var after=game.simulate(game.legalActions().stream().filter(a->a.id().equals(chosen)).findFirst().orElseThrow());
        assertTrue(after.inCheck(false) || !after.legalActions().isEmpty(),chosen);
    }
    @Test void findsMatingLineOnExpandedBoard() {
        var game=mateInTwo(); game.expandBoard(1,0,0,2);
        var hints=TacticalHints.forPosition(game);
        var plan=TestSessions.ai().analyze(game,TacticalHints.saferActionIds(hints));
        assertEquals(3,plan.matePlies());
        assertFalse(plan.competitiveActions().isEmpty());
    }

    private void convertsAgainstKingSeekingOpenSpace(char heavy, boolean expanded, int maxPlies) {
        var game=RulesTest.empty(); game.board.clear();
        game.board.set(0,PieceFactory.create(1,'K',true));
        game.board.set(1,PieceFactory.create(3,heavy,true));
        game.board.set(36,PieceFactory.create(2,'K',false));
        if(expanded)game.expandBoard(1,0,0,2);
        var ai=TestSessions.ai();
        for(int ply=0;ply<maxPlies && game.result.isEmpty();ply++) {
            if(game.whiteTurn) game.play(ai.choose(game));
            else {
                var defender=game;
                var escape=game.legalActions().stream().max(java.util.Comparator.comparingInt(a->{
                    if(defender.pieceAt(a.to())!=null) return 10000;
                    var next=defender.simulate(a); var geo=next.boardGeometry();
                    int edge=Math.min(Math.min(geo.file(a.to()),geo.width()-1-geo.file(a.to())),
                            Math.min(geo.rank(a.to()),geo.height()-1-geo.rank(a.to())));
                    int king=0;
                    for(int q=0;q<geo.size();q++) {var p=next.pieceAt(q);if(p!=null&&p.white()&&p.type()=='K')king=q;}
                    int distance=Math.max(Math.abs(geo.file(king)-geo.file(a.to())),Math.abs(geo.rank(king)-geo.rank(a.to())));
                    return edge*100+next.movementOptions(a.to()).size()*10+distance;
                })).orElseThrow();
                game.play(escape.id());
            }
        }
        assertEquals("white",game.result,"Heavy-piece advantage should become mate, history: "+game.history);
    }

    @Test void convertsQueenAdvantageAgainstKingSeekingOpenSpace() {
        convertsAgainstKingSeekingOpenSpace('Q',false,80);
    }

    @Test void convertsRookAdvantageAgainstKingSeekingOpenSpace() {
        convertsAgainstKingSeekingOpenSpace('R',false,120);
    }

    @Test void convertsQueenAdvantageOnExpandedBoard() {
        convertsAgainstKingSeekingOpenSpace('Q',true,120);
    }

    @Test void advancesKingToSupportRookInsteadOfShuffling() {
        var g=RulesTest.empty();g.board.clear();
        g.board.set(12,PieceFactory.create(1,'K',true));g.board.set(32,PieceFactory.create(3,'R',true));g.board.set(27,PieceFactory.create(2,'K',false));
        var hints=TacticalHints.forPosition(g);var r=TestSessions.ai().analyze(g,TacticalHints.saferActionIds(hints));
        assertEquals("move:12:21:",r.actionId(),"Kf3 supports the rook barrier instead of another rook waiting move");
    }

    @Test void fullHandDoesNotHideMatingContinuationBehindHundredsOfCardTargets() {
        var game=mateInTwo();
        RulesTest.card(game,chess.cards.CardType.TRAP);
        RulesTest.card(game,chess.cards.CardType.HIDDEN_TRAP);
        RulesTest.card(game,chess.cards.CardType.SCOUT);
        var hints=TacticalHints.forPosition(game);
        var review=TestSessions.ai().analyze(game,TacticalHints.saferActionIds(hints));
        assertEquals(3,review.matePlies());
        assertNotEquals("draw",review.actionId());
    }

    public static Game mateWithFreeKnight() {
        var game = mateInTwo();
        game.board.set(56, PieceFactory.create(5, 'N', false));
        return game;
    }

    @Test void choosesForcedMateInsteadOfFreeKnight() {
        var game = mateWithFreeKnight(); // Конь a8 доступен ладье c8
        var hints = TacticalHints.forPosition(game);
        String capture = "move:58:56:";
        assertTrue(game.legalActions().stream().anyMatch(a -> a.id().equals(capture)));
        var allowed = TacticalHints.saferActionIds(game, hints);
        assertTrue(allowed.contains(capture), "Useful captures remain available");
        var plan = TestSessions.ai().analyze(game, allowed);
        assertEquals(3, plan.matePlies(), "A free knight must not hide mate in two");
        assertNotEquals(capture, plan.actionId());
        assertEquals(plan.actionId(), TestSessions.ai().choose(game));
    }

}
