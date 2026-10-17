package chess.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.ToNumberPolicy;
import java.util.Map;

/** Единые настройки Gson для HTTP, сохранений и обмена с ИИ. */
public final class JsonCodec {
    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .disableHtmlEscaping()
            .setStrictness(Strictness.STRICT)
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create();

    private JsonCodec() {}

    public static String toJson(Object value) { return GSON.toJson(value); }

    /** API и файлы партии ожидают объект, а не массив или отдельное значение. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readObject(String json) {
        try {
            Object value = GSON.fromJson(json, Object.class);
            if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Expected JSON object");
            return (Map<String, Object>) value;
        } catch (JsonParseException e) {
            // Сохраняем контракт ошибок ввода: HTTP-контроллер отвечает статусом 400.
            throw new IllegalArgumentException("Invalid JSON object", e);
        }
    }
}
