package chess.ai;

import java.util.List;

/**
 * Короткая тактическая проверка одного действия: материал после него и после лучшего ответа,
 * матовые угрозы и шаховые вилки. Значения материала — в сотых пешки относительно позиции до действия.
 *
 * @param actionId                         проверенное действие
 * @param immediateMaterialDelta           изменение материала сразу после действия
 * @param worstMaterialDeltaAfterOneReply  худший итог после одного взятия соперника
 * @param dangerousReplyId                 ответ, дающий этот худший итог ("" если нет)
 * @param mateInOneReplyId                 ответ соперника, ставящий мат ("" если нет)
 * @param mateInOne                        действие ставит проверенный мат (у соперника нет неизвестных карт)
 * @param mateThreat                       после действия у соперника нет ответов по видимой позиции
 * @param worstMaterialDeltaAfterRecapture худший итог с учётом продолжения размена и вилок
 * @param checkingForkReplyId              ответ, начинающий шаховую вилку конём ("" если нет)
 */
public record TacticalHint(String actionId, int immediateMaterialDelta, int worstMaterialDeltaAfterOneReply,
                           String dangerousReplyId, String mateInOneReplyId, boolean mateInOne, boolean mateThreat,
                           int worstMaterialDeltaAfterRecapture, String checkingForkReplyId) {

    /** Простая подсказка без угроз: итог размена равен итогу после одного ответа. */
    public static TacticalHint material(String actionId, int afterReply) {
        return new TacticalHint(actionId, 0, afterReply, "", "", false, false, afterReply, "");
    }

    /** Соперник может поставить мат сразу после этого действия. */
    public boolean allowsMateInOne() { return !mateInOneReplyId.isEmpty(); }

    /** Материальный итог, по которому отсекаются явные потери. */
    public int materialSafety() { return worstMaterialDeltaAfterRecapture; }

    /** Строка кандидата для ChatGPT: порядок полей описан в промпте соперника. */
    public List<Object> candidateRow() {
        return List.of(actionId, immediateMaterialDelta, worstMaterialDeltaAfterOneReply, dangerousReplyId,
                mateInOneReplyId, worstMaterialDeltaAfterRecapture, mateInOne, mateThreat, checkingForkReplyId);
    }
}
