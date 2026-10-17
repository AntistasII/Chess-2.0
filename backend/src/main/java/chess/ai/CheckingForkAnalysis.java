package chess.ai;

import chess.game.Game;

/** Короткая форсированная линия: шах конём, лучшая защита, взятие и обратный размен. */
final class CheckingForkAnalysis {
    private CheckingForkAnalysis() {}

    static boolean possibleOnNextMove(Game position, boolean side) {
        int king = -1;
        var targets = new java.util.ArrayList<Integer>();
        for (int square = 0; square < position.boardGeometry().size(); square++) {
            var p = position.pieceAt(square);
            if (p == null || p.white() != side) continue;
            if (p.type() == 'K') king = square;
            else if (PositionEvaluator.materialValue(p.type()) >= 320) targets.add(square);
        }
        if (king < 0 || targets.isEmpty()) return false;
        for (int square = 0; square < position.boardGeometry().size(); square++) {
            var p = position.pieceAt(square);
            if (p == null || p.white() == side || p.type() != 'N') continue;
            for (var move : position.movementOptions(square)) {
                if (!chess.pieces.Knight.reaches(position.boardGeometry(), move.to(), king)) continue;
                if (targets.stream().anyMatch(t -> chess.pieces.Knight.reaches(position.boardGeometry(), move.to(), t))) return true;
            }
        }
        return false;
    }

    static int materialAfterBestDefence(Game checked, boolean side, int baseline, int knightSquare) {
        int current = TacticalHints.material(checked, side) - baseline;
        var knight = checked.pieceAt(knightSquare);
        if (knight == null || knight.type() != 'N' || knight.white() == side || knight.frozen() || knight.astral()) return current;
        boolean hasTarget = checked.movementOptions(knightSquare).stream().anyMatch(a -> {
            var victim = checked.pieceAt(a.to());
            return victim != null && victim.white() == side && PositionEvaluator.materialValue(victim.type()) >= 320;
        });
        if (!hasTarget) return current;
        int best = Integer.MIN_VALUE;
        for (var defence : checked.legalActions()) {
            Game next = checked.simulate(defence);
            int worst = TacticalHints.material(next, side) - baseline;
            var attacker = next.pieceAt(knightSquare);
            // Взятие, астрал, заморозка или отмена хода могут убрать продолжение вилки.
            if (attacker != null && attacker.id() == knight.id() && !attacker.frozen() && !attacker.astral()) {
                for (var capture : next.movementOptions(knightSquare)) {
                    var victim = next.pieceAt(capture.to());
                    if (victim == null || victim.white() != side) continue;
                    Game afterCapture = next.simulate(capture);
                    if (afterCapture.inCheck(!side)) continue;
                    int recovered = ExchangeAnalysis.recover(afterCapture, side, baseline, capture.to());
                    worst = Math.min(worst, recovered);
                }
            }
            best = Math.max(best, worst);
            // Этот шах не доказывает потерю материала, если есть хотя бы одна такая защита.
            if (best >= current) return current;
        }
        return best == Integer.MIN_VALUE ? current : best;
    }
}
