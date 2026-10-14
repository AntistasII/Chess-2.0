package chess.cards;

/** Виды заклинаний; UNKNOWN скрывает неизвестную карту при расчётах ИИ. */
public enum CardType {
    // Усиления действуют до следующего движения выбранной фигуры.
    PAWN_BOOST, KING_BOOST,
    // Возрождение использует список взятых фигур, смена стороны — вражескую пешку.
    REVIVE_PAWN, REVIVE_PIECE, CONVERT,
    // Эффекты на доске и перестановка своих фигур.
    FREEZE, ASTRAL, SHIELD, CLEANSE, SWAP,
    // Управление колодой и руками игроков.
    SCOUT, EXCHANGE, UNKNOWN
}
