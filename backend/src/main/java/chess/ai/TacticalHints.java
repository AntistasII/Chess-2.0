package chess.ai;

import chess.pieces.PieceType;
import chess.game.Game;
import java.util.*;

/** Короткая проверка потерь, мата и форсированных шаховых вилок; не полный поиск партии. */
public final class TacticalHints {
    private TacticalHints() {}

    public static List<TacticalHint> forPosition(Game position) {
        return forPosition(position, true);
    }

    private static List<TacticalHint> forPosition(Game position, boolean followCountercheck) {
        boolean side = position.whiteTurn();
        int baseline = material(position, side);
        var hints = new ArrayList<TacticalHint>();
        for (var action : position.legalActions()) {
            Game next = position.simulate(action);
            int immediate = material(next, side) - baseline;
            int worst = immediate;
            int worstAfterRecapture = immediate;
            String replyId = "";
            String mateReplyId = "";
            String checkingForkReplyId = "";
            var replies = next.legalActions();
            boolean givesCheck = next.inCheck(!side);
            // UNKNOWN не умеет применять карты. Отсутствие ответов на наблюдаемой
            // позиции — матовая угроза, но не доказательство отсутствия скрытого спасения.
            boolean mateThreat = replies.isEmpty() && next.inCheck(!side);
            boolean mateInOne = mateThreat && next.cards(!side).stream()
                    .noneMatch(c -> c.type() == chess.cards.CardType.UNKNOWN);
            for (var reply : replies) {
                if (!reply.isMove()) continue;
                Game afterReply = next.simulate(reply);
                // Даже тихий шах может быть матом. У защищающейся стороны
                // остаются её реальные карты, поэтому учитываем карточные спасения.
                if (afterReply.inCheck(side)) {
                    if (afterReply.legalActions().isEmpty()) mateReplyId = reply.id();
                    else if (afterReply.pieceAt(reply.to()).type() == PieceType.KNIGHT) {
                        int fork = CheckingForkAnalysis.materialAfterBestDefence(afterReply, side, baseline, reply.to());
                        if (fork < material(afterReply, side) - baseline && fork < worstAfterRecapture) {
                            worstAfterRecapture = fork;
                            checkingForkReplyId = reply.id();
                        }
                    }
                }
                // Свой шах может лишь отсрочить вилку. После вынужденного ответа
                // проверяем лучшую защиту на следующем своём действии, без рекурсии шахов.
                if (followCountercheck && givesCheck && immediate >= 320 && CheckingForkAnalysis.possibleOnNextMove(afterReply, side)) {
                    var continuation = forPosition(afterReply, false);
                    var safe = saferActionIds(continuation);
                    if (continuation.stream().noneMatch(TacticalHint::mateThreat)) {
                        int best = continuation.stream().filter(h -> safe.contains(h.actionId()))
                                .mapToInt(TacticalHint::materialSafety).max().orElse(0);
                        int value = material(afterReply, side) - baseline + Math.min(0, best);
                        if (value < worstAfterRecapture) {
                            worstAfterRecapture = value;
                            checkingForkReplyId = reply.id();
                        }
                    }
                }
                // Проверяем взятия (включая en passant) и превращения, без скрытых чужих карт.
                if (next.pieceAt(reply.to()) == null && !reply.option().matches("[QRBN]")
                        && !(next.pieceAt(reply.from()).type() == PieceType.PAWN
                            && reply.from() % next.boardGeometry().width() != reply.to() % next.boardGeometry().width())) continue;
                int value = material(afterReply, side) - baseline;
                if (value < worst) { worst = value; replyId = reply.id(); }
                // Не считаем честный размен потерей: проверяем легальное ответное взятие или карту.
                int recovered = value;
                if (value < immediate)
                    recovered = ExchangeAnalysis.recover(afterReply, side, baseline, reply.to());
                worstAfterRecapture = Math.min(worstAfterRecapture, recovered);
            }
            hints.add(new TacticalHint(action.id(), immediate, worst, replyId, mateReplyId, mateInOne, mateThreat,
                    worstAfterRecapture, checkingForkReplyId));
        }
        return hints;
    }

    /** Мат в один ход имеет приоритет; затем исключаем заметно худшие по материалу варианты. */
    public static Set<String> saferActionIds(List<TacticalHint> hints) {
        return saferActionIds(hints, false);
    }

    /** При решающем перевесе безопасная матовая атака важнее обязательного взятия. */
    public static Set<String> saferActionIds(Game position, List<TacticalHint> hints) {
        return saferActionIds(hints, EndgameConversion.active(position, position.whiteTurn()));
    }

    private static Set<String> saferActionIds(List<TacticalHint> hints, boolean converting) {
        var mates = hints.stream().filter(TacticalHint::mateInOne).toList();
        if (!mates.isEmpty()) return actionIds(mates);
        var survivors = hints.stream().filter(h -> !h.allowsMateInOne()).toList();
        // Если мат неизбежен, всё равно возвращаем допустимые варианты.
        var pool = survivors.isEmpty() ? hints : survivors;
        // Наличие неизвестной руки не повод предпочитать пешку ходу, вынуждающему
        // карточное спасение или сразу заканчивающему партию.
        var threats = pool.stream().filter(TacticalHint::mateThreat).toList();
        if (!threats.isEmpty()) pool = threats;
        int best = pool.stream().mapToInt(TacticalHint::materialSafety).max().orElse(0);
        // Не отсекаем тихие матовые продолжения только потому, что доступен
        // бесплатный конь. Отрицательные материальные исходы по-прежнему исключаем.
        int threshold = converting && best >= 0 ? 0 : best - 75;
        var allowed = new HashSet<String>();
        for (var hint : pool)
            if (hint.materialSafety() >= threshold) allowed.add(hint.actionId());
        return allowed;
    }

    private static Set<String> actionIds(List<TacticalHint> hints) {
        var ids = new HashSet<String>();
        for (var hint : hints) ids.add(hint.actionId());
        return ids;
    }

    static int material(Game game, boolean side) {
        int total = 0;
        for (int square = 0; square < game.boardGeometry().size(); square++) {
            var piece = game.pieceAt(square);
            if (piece == null) continue;
            int value = PositionEvaluator.materialValue(piece.type());
            total += piece.white() == side ? value : -value;
        }
        return total;
    }
}
