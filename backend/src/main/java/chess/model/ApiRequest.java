package chess.model;

import java.util.Map;

/**
 * Данные запроса; не читает HTTP и не изменяет партию.
 */
public record ApiRequest(GameOperation operation, boolean post, Map<String, Object> input) {
    public boolean isPost() {
        return post;
    }

    public boolean isAi() {
        return operation == GameOperation.AI;
    }

    /**
     * Отсутствующая версия заведомо устарела; проверка сессии вернёт 409.
     */
    public long version() {
        return input.get("version") instanceof Number number ? number.longValue() : -1;
    }

    public String text(String name, String fallback) {
        return (String) input.getOrDefault(name, fallback);
    }

    public int integer(String name, int fallback) {
        return ((Number) input.getOrDefault(name, fallback)).intValue();
    }

    public Integer optionalInteger(String name) {
        return input.containsKey(name) ? ((Number) input.get(name)).intValue() : null;
    }

    public int cardId() {
        return ((Number) input.get("cardId")).intValue();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> save() {
        if (!(input.get("save") instanceof Map<?, ?> data))
            throw new IllegalArgumentException("Некорректный файл сохранения.");
        return (Map<String, Object>) data;
    }
}
