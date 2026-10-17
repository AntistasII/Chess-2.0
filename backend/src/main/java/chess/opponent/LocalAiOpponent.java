package chess.opponent;

import chess.game.Game;
import chess.game.MoveSelector;

import java.util.Objects;

/**
 * Локальный компьютерный соперник; стратегию можно подменить в тестах.
 */
public final class LocalAiOpponent extends Opponent {
    private final MoveSelector strategy;

    public LocalAiOpponent(MoveSelector strategy) {
        this.strategy = Objects.requireNonNull(strategy);
    }

    @Override
    public String mode() {
        return "ai";
    }

    @Override
    public boolean automatic() {
        return true;
    }

    @Override
    public String choose(Game game) {
        return strategy.choose(game);
    }
}
