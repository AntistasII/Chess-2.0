#!/bin/sh
set -eu
. "$(dirname "$0")/java-env.sh"
cd "$PROJECT_ROOT"
sh scripts/compile-java.sh --tests
"$CHESS_JAVA" -ea -cp backend/test-out chess.game.RulesTest
"$CHESS_JAVA" -ea -cp backend/test-out chess.game.GameSessionTest
"$CHESS_JAVA" -ea -cp backend/test-out chess.game.GameSaveTest
"$CHESS_JAVA" -ea -cp backend/test-out chess.api.NetworkApiTest
"$CHESS_JAVA" -ea -cp backend/test-out chess.board.BoardTest
"$CHESS_JAVA" -ea -cp backend/test-out chess.game.ExpandedBoardTest

"$CHESS_JAVA" -ea -cp backend/test-out chess.ai.ChatGptOpponentTest
"$CHESS_JAVA" -ea -cp backend/test-out chess.config.ApplicationConfigTest
"$CHESS_JAVA" -ea -cp backend/test-out chess.ai.OpponentMemoryTest
