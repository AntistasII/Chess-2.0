package chess.serialization;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class JsonCodecTest {
    @Test void preservesNullBoardCellsUnicodeAndLargeVersions() {
        var state = new LinkedHashMap<String, Object>();
        state.put("board", Arrays.asList(null, Map.of("type", "P"), null));
        state.put("selection", null);
        state.put("text", "Шах\n\"мат\" <>&");
        state.put("version", 9007199254740993L);
        var restored = JsonCodec.readObject(JsonCodec.toJson(state));
        assertEquals(state, restored);
        assertTrue(restored.containsKey("selection"));
        assertEquals(9007199254740993L, restored.get("version"));
    }

    @Test void rejectsNonObjectsMalformedJsonAndTrailingData() {
        for (String input : new String[]{"null", "[]", "[[\"key\",1]]", "42", "true", "\"text\"", "",
                "{bad:1}", "{'key':1}", "{\"key\":1,}", "{} {}", "{\"key\":NaN}", "{\"key\":1 // comment\n}"}) {
            assertThrows(IllegalArgumentException.class, () -> JsonCodec.readObject(input), input);
        }
    }
}
