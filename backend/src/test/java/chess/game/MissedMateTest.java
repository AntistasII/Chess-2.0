package chess.game;

import chess.ai.TacticalHints;
import chess.serialization.JsonCodec;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

public class MissedMateTest {
    public static Game position() throws Exception {
        try(var stream=MissedMateTest.class.getResourceAsStream("/ai/missed-mate-138.json")) {
            return GameSave.decode(JsonCodec.readObject(new String(stream.readAllBytes(),StandardCharsets.UTF_8)));
        }
    }
    @Test void choosesMateInsteadOfCapturingPawnDespiteHiddenEnemyCards() throws Exception {
        var game=position();
        var mate=game.legalActions().stream().filter(a->a.id().equals("move:13:14:")).findFirst().orElseThrow();
        var next=game.simulate(mate);
        assertTrue(next.inCheck(true)); assertTrue(next.legalActions().isEmpty(),"Actual CLEANSE and SCOUT cannot save the king");
        var observation=game.observationForCurrentPlayer();
        var hints=TacticalHints.forPosition(observation);
        var allowed=TacticalHints.saferActionIds(hints);
        assertTrue(allowed.contains(mate.id()),"Hidden cards must not make mating attack disappear behind a pawn capture");
        assertFalse(allowed.contains("move:13:20:"),"Qxe3 delays the available finish");
        String choice=TestSessions.ai().choose(game);
        var chosen=game.legalActions().stream().filter(a->a.id().equals(choice)).findFirst().orElseThrow();
        var result=game.simulate(chosen);
        assertTrue(result.inCheck(true) && result.legalActions().isEmpty(),choice);
    }
    @Test void distinguishesVerifiedMateFromThreatAgainstUnknownHand() throws Exception {
        var game=position();
        var known=TacticalHints.forPosition(game).stream().filter(h->h.get("actionId").equals("move:13:14:")).findFirst().orElseThrow();
        var hidden=TacticalHints.forPosition(game.observationForCurrentPlayer()).stream().filter(h->h.get("actionId").equals("move:13:14:")).findFirst().orElseThrow();
        assertEquals(true,known.get("mateInOne"));
        assertEquals(false,hidden.get("mateInOne"));
        assertEquals(true,hidden.get("mateThreat"));
    }

    @Test void unknownCardsDoNotLeakAndRealCardRescueStillPreventsGameOver() throws Exception {
        var original=position();
        var withRescue=position();
        withRescue.whiteHand.clear();
        var astral=chess.cards.CardCatalog.create(3000,chess.cards.CardType.ASTRAL);
        withRescue.whiteHand.add(astral);
        withRescue.whiteHand.add(chess.cards.CardCatalog.create(3001,chess.cards.CardType.CLEANSE));
        var actual=TacticalHints.forPosition(withRescue).stream().filter(h->h.get("actionId").equals("move:13:14:")).findFirst().orElseThrow();
        assertEquals(false,actual.get("mateInOne")); assertEquals(false,actual.get("mateThreat"));
        var hiddenOriginal=TacticalHints.forPosition(original.observationForCurrentPlayer());
        var hiddenRescue=TacticalHints.forPosition(withRescue.observationForCurrentPlayer());
        assertEquals(hiddenOriginal,hiddenRescue,"Unknown hand contents must not influence AI hints");
        withRescue.play("move:13:14:");
        assertTrue(withRescue.result.isEmpty(),"Potential mate must not end the game when a real escape exists");
        RulesTest.cast(withRescue,astral,14);
        assertFalse(withRescue.inCheck(true));
    }

}
