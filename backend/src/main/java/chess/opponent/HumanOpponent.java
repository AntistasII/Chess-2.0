package chess.opponent;

/** Второй человек за тем же компьютером: оба игрока действуют вручную. */
public final class HumanOpponent extends Opponent {
    @Override public String mode() { return "human"; }
}
