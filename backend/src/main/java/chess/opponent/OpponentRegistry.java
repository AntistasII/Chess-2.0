package chess.opponent;

import chess.game.Game;
import chess.game.MoveSelector;
import java.util.Map;
import java.util.Objects;

/** Выбирает реализацию по режиму партии, включая режим из загруженного сохранения. */
public final class OpponentRegistry {
    private final Map<String, Opponent> opponents;

    public OpponentRegistry(MoveSelector local, MoveSelector chatGpt) {
        opponents = new java.util.HashMap<>(Map.of("ai", adapt("ai", local), "chatgpt", adapt("chatgpt", chatGpt),
                "network", new NetworkOpponent(), "human", new HumanOpponent()));
    }

    public Opponent forMode(String mode) {
        Opponent opponent = opponents.get(mode);
        if (opponent == null) throw new IllegalArgumentException("Неизвестный соперник: " + mode);
        return opponent;
    }

    public boolean chatGptConfigured() {
        return opponents.get("chatgpt") instanceof ChatGptOpponent chat && chat.configured();
    }

    public void configureChatGptKey(String apiKey) {
        if (!(opponents.get("chatgpt") instanceof ChatGptOpponent chat))
            throw new IllegalArgumentException("Настройка API-ключа для этого соперника недоступна");
        opponents.put("chatgpt", chat.withApiKey(apiKey));
    }

    /** Сохраняет возможность подключать простые тестовые стратегии через MoveSelector. */
    private static Opponent adapt(String mode, MoveSelector strategy) {
        Objects.requireNonNull(strategy);
        if (strategy instanceof Opponent opponent) {
            if (!opponent.mode().equals(mode)) throw new IllegalArgumentException("Не совпадает режим соперника");
            return opponent;
        }
        if (mode.equals("ai")) return new LocalAiOpponent(strategy);
        return new Opponent() {
            @Override public String mode() { return mode; }
            @Override public boolean automatic() { return true; }
            @Override public boolean requiresScoutPreview() { return true; }
            @Override public String choose(Game game) { return strategy.choose(game); }
        };
    }
}
