package chess;

import org.junit.jupiter.api.Test;

/** Существующие сценарии правил и протокола выполняются и через обычный mvn test. */
class LegacySuiteTest {
    @Test void rules() { chess.game.RulesTest.main(new String[0]); }
    @Test void session() throws Exception { chess.game.GameSessionTest.main(new String[0]); }
    @Test void saves() throws Exception { chess.game.GameSaveTest.main(new String[0]); }
    @Test void network() throws Exception { chess.api.NetworkApiTest.main(new String[0]); }
    @Test void board() { chess.board.BoardTest.main(new String[0]); }
    @Test void expansion() { chess.game.ExpandedBoardTest.main(new String[0]); }
    @Test void chatGpt() throws Exception { chess.opponent.ChatGptOpponentTest.main(new String[0]); }
    @Test void memory() throws Exception { chess.opponent.OpponentMemoryTest.main(new String[0]); }
}
