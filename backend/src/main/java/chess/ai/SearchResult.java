package chess.ai;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Оценки завершённой итерации или отдельно проверенная матовая линия по известной позиции. */
public record SearchResult(String actionId, Map<String, Double> scores, int depth, int matePlies) {
    public SearchResult(String actionId, Map<String, Double> scores, int depth) { this(actionId, scores, depth, 0); }
    public SearchResult { scores = Map.copyOf(scores); if (matePlies < 0) throw new IllegalArgumentException(); }

    /** Сохраняет близкие по оценке варианты; незавершённый поиск не ограничивает выбор. */
    public Set<String> competitiveActions() {
        if (matePlies > 0) return Set.of(actionId);
        if (scores.isEmpty()) return Set.of();
        double best = scores.values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        double margin = depth >= 2 ? 100 : 70;
        return scores.entrySet().stream().filter(e -> e.getValue() >= best - margin)
                .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }
}
