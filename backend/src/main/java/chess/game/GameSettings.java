package chess.game;

/** Неизменяемые настройки партии, выбранные при её создании. */
/** @param handLimit начальная вместимость руки обоих игроков */
public record GameSettings(GameMode mode, int handLimit, Difficulty difficulty) {
    public static final int MIN_HAND_LIMIT = 1, MAX_HAND_LIMIT = 12;

    public GameSettings {
        if (mode == null) throw new IllegalArgumentException("Неизвестный режим");
        if (difficulty == null) throw new IllegalArgumentException("Неизвестная сложность");
        if (handLimit < MIN_HAND_LIMIT || handLimit > MAX_HAND_LIMIT) throw new IllegalArgumentException("Лимит карт: от 1 до 12");
    }

    /** Разбирает значения из настроек, HTTP-запроса или сохранения. */
    public static GameSettings parse(String mode, int handLimit, String difficulty) {
        return new GameSettings(GameMode.fromId(mode), handLimit, Difficulty.fromId(difficulty));
    }
}
