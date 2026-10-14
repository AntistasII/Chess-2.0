package chess.ai;

import chess.game.Game;
import java.util.*;

/** Одно ответное действие: предупреждения о потерях, а не полноценная оценка шахматного движка. */
public final class TacticalHints {
    private TacticalHints() {}

    public static List<Map<String, Object>> forPosition(Game position) {
        boolean side = position.whiteTurn();
        int baseline = material(position, side);
        var hints = new ArrayList<Map<String, Object>>();
        for (var action : position.legalActions()) {
            Game next = position.simulate(action);
            int immediate = material(next, side) - baseline;
            int worst = immediate;
            String replyId = "";
            String mateReplyId = "";
            for (var reply : next.legalActions()) {
                if (!reply.kind().equals("move")) continue;
                Game afterReply = next.simulate(reply);
                // Даже тихий шах может быть матом. У защищающейся стороны
                // остаются её реальные карты, поэтому учитываем карточные спасения.
                if (afterReply.inCheck(side) && afterReply.legalActions().isEmpty())
                    mateReplyId = reply.id();
                // Проверяем взятия (включая en passant) и превращения, без скрытых чужих карт.
                if (next.pieceAt(reply.to()) == null && !reply.option().matches("[QRBN]")
                        && !(next.pieceAt(reply.from()).type() == 'P'
                            && reply.from() % next.boardGeometry().width() != reply.to() % next.boardGeometry().width())) continue;
                int value = material(afterReply, side) - baseline;
                if (value < worst) { worst = value; replyId = reply.id(); }
            }
            hints.add(Map.of("actionId", action.id(), "immediateMaterialDelta", immediate,
                    "worstMaterialDeltaAfterOneReply", worst, "dangerousReplyId", replyId,
                    "mateInOneReplyId", mateReplyId));
        }
        return hints;
    }

    /** Не предлагаем явную потерю фигуры, если есть вариант безопаснее минимум на 250. */
    public static Set<String> saferActionIds(List<Map<String, Object>> hints) {
        var survivors = hints.stream().filter(h -> "".equals(h.getOrDefault("mateInOneReplyId", ""))).toList();
        // Если мат неизбежен, всё равно возвращаем допустимые варианты.
        var pool = survivors.isEmpty() ? hints : survivors;
        int best = pool.stream().mapToInt(h -> (Integer) h.get("worstMaterialDeltaAfterOneReply")).max().orElse(0);
        var allowed = new HashSet<String>();
        for (var hint : pool) {
            int value = (Integer) hint.get("worstMaterialDeltaAfterOneReply");
            if (value >= best - 250 || value >= 0) allowed.add((String) hint.get("actionId"));
        }
        return allowed;
    }

    private static int material(Game game, boolean side) {
        int total = 0;
        for (int square = 0; square < game.boardGeometry().size(); square++) {
            var piece = game.pieceAt(square);
            if (piece == null) continue;
            int value = switch (piece.type()) {
                case 'P' -> 100;
                case 'N' -> 320;
                case 'B' -> 335;
                case 'R' -> 500;
                case 'Q' -> 900;
                default -> 0;
            };
            total += piece.white() == side ? value : -value;
        }
        return total;
    }
}
