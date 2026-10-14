package chess.ai;

import chess.game.Action;
import chess.game.Game;

import java.util.*;

/**
 * Локальный ИИ: ограниченный поиск своего действия и ответа соперника без знания скрытых карт.
 */
public final class Ai {
    private Ai() {
    }

    /**
     * Выбирает легальное действие; сложность ограничивает ширину поиска и время расчёта.
     */
    public static String choose(Game live) {
        List<Action> legal = live.legalActions();
        if (legal.isEmpty()) throw new IllegalStateException("Нет действий");
        boolean side = live.whiteTurn();
        Game observation = live.observationForCurrentPlayer();
        int count = observation.cards(!side).size();
        var rng = new Random();
        int width = switch (live.difficulty()) {
            case "easy" -> 6;
            case "hard" -> 24;
            default -> 14;
        };
        long deadline = System.nanoTime() + switch (live.difficulty()) {
            case "easy" -> 300_000_000L;
            case "hard" -> 2_000_000_000L;
            default -> 900_000_000L;
        };
        // Действие, полученная позиция и её предварительная оценка.
        record Candidate(Action action, Game game, double value) {
        }
        var candidates = new ArrayList<Candidate>();
        for (var a : legal) {
            Game next = observation.simulate(a);
            candidates.add(new Candidate(a, next, PositionEvaluator.evaluate(next, side)));
        }
        // Сначала проверяем самые перспективные действия, затем худший ответ противника.
        candidates.sort(Comparator.comparingDouble(Candidate::value).reversed());
        String best = candidates.get(0).action.id();
        double bestScore = -Double.MAX_VALUE;
        for (var c : candidates.subList(0, Math.min(width, candidates.size()))) {
            double worst = c.value;
            if (!live.difficulty().equals("easy")) {
                List<Action> replies = c.game.legalActions();
                if (replies.isEmpty()) {
                    worst = c.game.inCheck(!side) ? (count == 0 ? 100_000 : c.value + 150) : 0;
                } else {
                    worst = Double.MAX_VALUE;
                    // Сначала взятия и превращения. Завершаем проверку кандидата:
                    // неполная оценка завышает качество хода при истечении времени.
                    replies.sort(Comparator.comparingInt((Action reply) ->
                            reply.kind().equals("move") && (c.game.pieceAt(reply.to()) != null
                                    || reply.option().matches("[QRBN]")) ? 0 : 1));
                    for (var reply : replies) {
                        Game next = c.game.simulate(reply);
                        worst = Math.min(worst, PositionEvaluator.evaluate(next, side));
                    }
                }
            }
            double score = worst + (live.difficulty().equals("easy") ? rng.nextDouble() * 90 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = c.action.id();
            }
            if (System.nanoTime() > deadline) break;
        }
        return best;
    }


}
