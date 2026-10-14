package chess.game;

import chess.ai.Ai;
import chess.cards.CardType;
import chess.pieces.Piece;
import java.util.Map;
import static chess.game.RulesTest.*;

/** Проверки шахматных правил и карт на доске с добавленными линиями. */
public final class ExpandedBoardTest {
    private static int checks;

    private static void verify(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Game g = empty();
        g.expandBoard(1, 1, 1, 1);
        var geometry = g.boardGeometry();
        verify(g.board.get(geometry.originalSquare(0, 4)).type() == 'K'
                && g.board.get(geometry.originalSquare(7, 4)).type() == 'K', "Короли переносятся вместе с исходной областью");
        int rook = geometry.index(3, 0), far = geometry.index(3, 9);
        g.board.set(rook, new Piece(3, 'R', true));
        verify(hasMove(g, rook, far), "Ладья ходит дальше семи клеток");
        g.board.set(geometry.index(3, 5), new Piece(4, 'N', true));
        verify(!hasMove(g, rook, far), "Проверка препятствий использует новую ширину");
        g.board.set(geometry.index(3, 5), null);
        g.board.set(geometry.index(7, 8), new Piece(5, 'N', true));
        verify(hasMove(g, geometry.index(7, 8), geometry.index(9, 9)), "Конь ходит в новые клетки за индексом 63");
        int pawn = geometry.originalSquare(1, 0);
        g.board.set(pawn, new Piece(6, 'P', true));
        verify(hasMove(g, pawn, pawn + 2 * g.board.width()), "Первый двойной ход на смещённой стартовой линии");
        g.board.get(pawn).enableBoost();
        verify(hasMove(g, pawn, pawn + 3 * g.board.width()), "Усиление использует смещённую стартовую линию");

        // Пешка превращается на новом внешнем краю, а не на прежнем восьмом ряду.
        g = empty(); g.expandBoard(1, 1, 1, 1); geometry = g.boardGeometry();
        int beforeEdge = geometry.index(8, 1), edge = geometry.index(9, 1);
        g.board.set(beforeEdge, new Piece(3, 'P', true));
        verify(g.legalActions().stream().filter(a -> a.from() == beforeEdge && a.to() == edge).count() == 4, "Превращение на новой границе");
        int oldEdge = geometry.originalSquare(7, 0);
        g.board.set(beforeEdge, null);
        g.board.set(geometry.originalSquare(6, 0), new Piece(3, 'P', true));
        verify(g.legalActions().stream().anyMatch(a -> a.to() == oldEdge && a.option().isEmpty()), "Старый край больше не является линией превращения");

        // Вертикальное и боковое расширение по отдельности дают прямоугольную доску.
        g = empty(); g.expandBoard(1, 1, 0, 0); geometry = g.boardGeometry();
        int diagonal = geometry.index(1, 0), diagonalEnd = geometry.index(7, 6);
        g.board.set(diagonal, new Piece(3, 'B', true));
        verify(hasMove(g, diagonal, diagonalEnd), "Диагональный ход на 10×8");
        g.board.set(geometry.index(4, 3), new Piece(4, 'P', true));
        verify(!hasMove(g, diagonal, diagonalEnd), "Препятствие на диагонали прямоугольной доски");
        g = empty(); g.expandBoard(0, 0, 1, 1); geometry = g.boardGeometry();
        int blackPawn = geometry.index(1, 0), blackEdge = geometry.index(0, 0);
        g.board.set(blackPawn, new Piece(3, 'P', false)); g.whiteTurn = false;
        verify(g.legalActions().stream().filter(a -> a.from() == blackPawn && a.to() == blackEdge).count() == 4,
                "Чёрная пешка превращается на новом нижнем краю 8×10");
        g = empty(); g.expandBoard(0, 0, 1, 1); geometry = g.boardGeometry();
        int attackingRook = geometry.index(9, 5), kingTarget = geometry.originalSquare(0, 4);
        g.board.set(attackingRook, new Piece(3, 'R', false));
        verify(!g.attacked(kingTarget, false), "Новая вертикаль не переносится в соседний ряд");
        g.board.set(attackingRook, null);
        g.board.set(geometry.index(9, 4), new Piece(3, 'R', false));
        g.board.set(geometry.originalSquare(7, 4), null);
        g.board.set(geometry.originalSquare(7, 7), new Piece(2, 'K', false));
        verify(g.inCheck(true), "Атака с нового верхнего ряда проходит по всей высоте");

        // Рокировка по-прежнему соединяет исходного короля и исходную ладью.
        g = empty();
        g.board.set(0, new Piece(3, 'R', true)); g.board.set(7, new Piece(4, 'R', true));
        g.wKingCastle = g.wQueenCastle = true;
        g.expandBoard(2, 1, 2, 1); geometry = g.boardGeometry();
        int king = geometry.originalSquare(0, 4);
        verify(g.legalActions().stream().filter(a -> a.option().equals("castle")).count() == 2, "Обе рокировки после вставки линий");
        move(g, king, king + 2);
        verify(g.board.get(king + 1).type() == 'R' && g.board.get(king + 2).type() == 'K', "Рокировка перемещает обе фигуры по новым индексам");

        g = empty(); g.board.set(0, new Piece(3, 'R', true)); g.wQueenCastle = true;
        g.expandBoard(1, 1, 1, 1); geometry = g.boardGeometry();
        move(g, geometry.originalSquare(0, 0), geometry.originalSquare(1, 0));
        verify(!g.wQueenCastle, "Ход исходной ладьи снимает право рокировки после расширения");

        // Геометрический перенос сохраняет связанную клетку взятия на проходе.
        g = empty(); g.board.set(36, new Piece(3, 'P', true)); g.board.set(51, new Piece(4, 'P', false));
        g.whiteTurn = false; move(g, 51, 35); g.expandBoard(1, 1, 1, 1); geometry = g.boardGeometry();
        verify(g.enPassant == geometry.originalSquare(5, 3), "Перенос клетки взятия на проходе");
        move(g, geometry.originalSquare(4, 4), geometry.originalSquare(5, 3));
        verify(g.board.get(geometry.originalSquare(4, 3)) == null && g.captured.size() == 1, "Взятие на проходе с новой шириной");

        g = empty(); g.captured.add(new Piece(3, 'P', true)); g.expandBoard(1, 1, 2, 1); geometry = g.boardGeometry();
        verify(g.startingSquares('P', true).size() == 8 && g.startingSquares('P', true).get(0) == geometry.originalSquare(1, 0), "Возрождение привязано к прежним восьми вертикалям");
        var revive = card(g, CardType.REVIVE_PAWN);
        cast(g, revive, geometry.originalSquare(1, 0));
        verify(!g.board.get(geometry.originalSquare(1, 0)).boost(), "Возрождённая пешка обычная и на смещённой доске");

        g = empty(); g.expandBoard(1, 1, 1, 1); geometry = g.boardGeometry();
        int enemy = geometry.index(9, 9);
        g.board.set(enemy, new Piece(3, 'R', false));
        var freeze = card(g, CardType.FREEZE); cast(g, freeze, enemy);
        verify(g.board.get(enemy).frozen(), "Карты доступны для фигур на новых клетках");
        Game copied = g.copy(); copied.expandBoard(1, 1, 0, 0);
        verify(g.board.width() == 10 && copied.board.width() == 12 && g.board.get(enemy).frozen(), "Поиск копирует расширенную доску независимо");
        Map<?, ?> size = (Map<?, ?>) GameView.of(g).get("boardSize");
        verify(size.get("width").equals(10) && size.get("height").equals(10) && size.get("originRank").equals(1), "API передаёт размеры и смещение");
        g.difficulty = "easy";
        String action = Ai.choose(g);
        verify(g.legalActions().stream().anyMatch(a -> a.id().equals(action)), "ИИ выбирает допустимое действие на расширенной доске");
        System.out.println("Passed " + checks + " expanded-board rule checks.");
    }
}
