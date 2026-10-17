package chess.opponent;

import chess.game.ReviewedParty;
import chess.game.TestSessions;
import chess.serialization.JsonCodec;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ChatGptSearchTest {
    @Test void sendsSearchScoresAndExcludesRecordedRookBlunderWithoutReplacingModelChoice() {
        var selected = new AtomicReference<String>();
        var opponent = new ChatGptOpponent("test-key", "test-model", "test", (key, body) -> {
            var request = JsonCodec.readObject(body);
            var input = JsonCodec.readObject((String)request.get("input"));
            var search = (Map<?, ?>)input.get("search");
            assertTrue(((Number)search.get("depth")).intValue() >= 1);
            var candidates = (List<?>)input.get("candidates");
            assertFalse(candidates.stream().anyMatch(row -> ((List<?>)row).getFirst().equals("move:58:42:")));
            assertEquals(candidates.size(), ((List<?>)search.get("evaluations")).size());
            String id = (String)((List<?>)candidates.getLast()).getFirst();
            selected.set(id);
            String text = JsonCodec.toJson(Map.of("actionId", id));
            return new ChatGptOpponent.Reply(200, JsonCodec.toJson(Map.of("status", "completed", "output",
                    List.of(Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", text)))))));
        }, TestSessions.ai());
        String choice = opponent.choose(ReviewedParty.before(78));
        assertNotNull(selected.get(), "Exercise transport, not a single forced action");
        assertEquals(selected.get(), choice);
    }
    @Test void offersMatingAttacksBeforePawnCaptureEvenWithoutLocalAdvisor() throws Exception {
        var game=chess.game.MissedMateTest.position();
        var opponent=new ChatGptOpponent("test-key","test-model","test",(key,body)->{
            var request=JsonCodec.readObject(body);
            var input=JsonCodec.readObject((String)request.get("input"));
            var candidates=(List<?>)input.get("candidates");
            assertFalse(candidates.isEmpty());
            for(Object row:candidates) {
                var candidate=(List<?>)row;
                assertEquals(9,candidate.size());
                assertEquals(false,candidate.get(6),"Hidden hands prevent certainty");
                assertEquals(true,candidate.get(7),"Every offered move must be a mating attack");
                assertNotEquals("move:13:20:",candidate.getFirst());
            }
            String id=(String)((List<?>)candidates.getFirst()).getFirst();
            String text=JsonCodec.toJson(Map.of("actionId",id));
            return new ChatGptOpponent.Reply(200,JsonCodec.toJson(Map.of("status","completed","output",
                    List.of(Map.of("type","message","content",List.of(Map.of("type","output_text","text",text)))))));
        });
        String choice=opponent.choose(game.observationForCurrentPlayer());
        var after=game.simulate(game.legalActions().stream().filter(a->a.id().equals(choice)).findFirst().orElseThrow());
        assertTrue(after.inCheck(true) && after.legalActions().isEmpty(),choice);
    }

}
