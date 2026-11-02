package chess.ai;

import chess.game.Action;
import chess.game.Game;
import java.util.List;
import java.util.Set;

/** Добор до лимита вместо выжидания, если короткая проверка не находит срочной задачи. */
public final class CardDrawPolicy {
    private CardDrawPolicy() {}

    public static boolean shouldDraw(Game position, List<TacticalHint> hints, Set<String> allowed) {
        if (EndgameConversion.active(position, position.whiteTurn())) return false;
        if (!allowed.contains(Action.DRAW_ID) || position.inCheck(position.whiteTurn())) return false;
        var draw = hints.stream().filter(h -> Action.DRAW_ID.equals(h.actionId())).findFirst().orElseThrow();
        if (draw.worstMaterialDeltaAfterOneReply() < 0 || draw.allowsMateInOne()) return false;
        boolean side = position.whiteTurn();
        double baseline = BaselineEvaluator.evaluate(position, side);
        for (var action : position.legalActions()) {
            if (!allowed.contains(action.id()) || action.isDraw()) continue;
            var hint = hints.stream().filter(h -> action.id().equals(h.actionId())).findFirst().orElseThrow();
            if (hint.immediateMaterialDelta() > 0) return false;
            Game next = position.simulate(action);
            // Не прерываем шахи, конкретную атаку или полезное применение карты ради пополнения.
            if (next.inCheck(!side) || BaselineEvaluator.evaluate(next, side) > baseline + 70) return false;
        }
        return true;
    }
}
