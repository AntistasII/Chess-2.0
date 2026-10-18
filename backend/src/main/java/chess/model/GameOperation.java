package chess.model;

/**
 * Операции приложения. URL переводится в операцию на границе HTTP-слоя.
 */
public enum GameOperation {
    STATE, LOGIN, NEW_GAME, RESET, PLAY, AI, SCOUT, DRAW, RESIGN, SAVE, LOAD, EXPORT, IMPORT, UNKNOWN
}
