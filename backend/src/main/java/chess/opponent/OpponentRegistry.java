package chess.opponent;

import chess.game.Game;
import chess.game.GameMode;
import chess.game.MoveSelector;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Соперник для каждого режима партии, включая режим из загруженного сохранения. */
public final class OpponentRegistry {
    private final Map<GameMode, Opponent> opponents = new EnumMap<>(GameMode.class);

    public OpponentRegistry(MoveSelector local, MoveSelector chatGpt) {
        register(adapt(GameMode.AI, local));
        register(adapt(GameMode.CHATGPT, chatGpt));
        register(new NetworkOpponent());
        register(new HumanOpponent());
    }

    /** Новый вид соперника подключается одной регистрацией. */
    public void register(Opponent opponent) {
        opponents.put(opponent.mode(), opponent);
    }

    public Opponent forMode(GameMode mode) {
        Opponent opponent = opponents.get(mode);
        if (opponent == null) throw new IllegalArgumentException("Неизвестный соперник: " + mode.id());
        return opponent;
    }

    public boolean chatGptConfigured() {
        return opponents.get(GameMode.CHATGPT) instanceof ChatGptOpponent chat && chat.configured();
    }

    public void configureChatGptKey(String apiKey) {
        if (!(opponents.get(GameMode.CHATGPT) instanceof ChatGptOpponent chat))
            throw new IllegalArgumentException("Настройка API-ключа для этого соперника недоступна");
        register(chat.withApiKey(apiKey));
    }

    /** Сохраняет возможность подключать простые тестовые стратегии через MoveSelector. */
    private static Opponent adapt(GameMode mode, MoveSelector strategy) {
        Objects.requireNonNull(strategy);
        if (strategy instanceof Opponent opponent) {
            if (opponent.mode() != mode) throw new IllegalArgumentException("Не совпадает режим соперника");
            return opponent;
        }
        if (mode == GameMode.AI) return new LocalAiOpponent(strategy);
        return new Opponent() {
            @Override public GameMode mode() { return mode; }
            @Override public boolean automatic() { return true; }
            @Override public boolean requiresScoutPreview() { return true; }
            @Override public String choose(Game game) { return strategy.choose(game); }
        };
    }
}
