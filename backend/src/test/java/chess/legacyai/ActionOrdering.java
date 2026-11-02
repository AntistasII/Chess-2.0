package chess.legacyai;

import chess.game.Action;
import chess.game.Game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Сначала рассматриваем прошлый лучший ход, взятия, превращения и карты. Ничего не отсекаем.
 */
final class ActionOrdering {
    private ActionOrdering() {
    }

    static List<Action> ordered(Game game, List<Action> actions, String preferred) {
        var ordered = new ArrayList<>(actions);
        ordered.sort(Comparator.comparingInt((Action a) -> priority(game, a, preferred)).reversed()
                .thenComparing(Action::id));
        return ordered;
    }

    static boolean forcing(Game game, Action action) {
        if (action.isCard()) return true;
        return action.isMove() && (game.pieceAt(action.to()) != null
                || promotion(action) || enPassant(game, action));
    }

    private static int priority(Game game, Action action, String preferred) {
        if (action.id().equals(preferred)) return 100_000;
        if (action.isDraw()) return -100;
        if (action.isCard()) return 500;
        int value = promotion(action) ? 2000 : 0;
        var victim = game.pieceAt(action.to());
        if (victim != null) value += 1000 + PositionEvaluator.materialValue(victim.type()) * 10
                - PositionEvaluator.materialValue(game.pieceAt(action.from()).type());
        if (enPassant(game, action)) value += 1000;
        return value;
    }

    private static boolean promotion(Action action) {
        return "QRBN".contains(action.option()) && !action.option().isEmpty();
    }

    private static boolean enPassant(Game game, Action action) {
        return game.pieceAt(action.from()).type() == chess.pieces.PieceType.PAWN
                && game.boardGeometry().file(action.from()) != game.boardGeometry().file(action.to());
    }
}
