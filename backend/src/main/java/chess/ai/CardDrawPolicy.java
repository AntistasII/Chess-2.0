package chess.ai;

import chess.game.Game;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Добор до лимита вместо выжидания, если короткая проверка не находит срочной задачи. */
public final class CardDrawPolicy {
    private CardDrawPolicy() {}

    public static boolean shouldDraw(Game position, List<Map<String, Object>> hints, Set<String> allowed) {
        if (EndgameConversion.active(position, position.whiteTurn())) return false;
        if (!allowed.contains("draw") || position.inCheck(position.whiteTurn())) return false;
        var draw = hints.stream().filter(h -> "draw".equals(h.get("actionId"))).findFirst().orElseThrow();
        if (((Number)draw.get("worstMaterialDeltaAfterOneReply")).intValue() < 0
                || !"".equals(draw.get("mateInOneReplyId"))) return false;
        boolean side = position.whiteTurn();
        double baseline = PositionEvaluator.evaluate(position, side);
        for (var action : position.legalActions()) {
            if (!allowed.contains(action.id()) || action.id().equals("draw")) continue;
            var hint = hints.stream().filter(h -> action.id().equals(h.get("actionId"))).findFirst().orElseThrow();
            if (((Number)hint.get("immediateMaterialDelta")).intValue() > 0) return false;
            Game next = position.simulate(action);
            // Не прерываем шахи, конкретную атаку или полезное применение карты ради пополнения.
            if (next.inCheck(!side) || PositionEvaluator.evaluate(next, side) > baseline + 70) return false;
        }
        return true;
    }
}
