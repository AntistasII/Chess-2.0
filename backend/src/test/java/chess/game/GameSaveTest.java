package chess.game;

import chess.serialization.JsonCodec;
import chess.cards.CardType;
import chess.cards.CardCatalog;
import chess.pieces.Piece;
import chess.pieces.PieceFactory;
import java.nio.file.*;
import java.util.*;
import static chess.game.RulesTest.*;

public final class GameSaveTest {
    private static void verify(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static Map<String, Object> snapshot(Game game) {
        var data = new LinkedHashMap<>(GameSave.encode(game));
        data.remove("gameId");
        return JsonCodec.readObject(JsonCodec.toJson(data));
    }
    private static Game roundTrip(Game game) {
        Game restored = GameSave.decode(JsonCodec.readObject(JsonCodec.toJson(GameSave.encode(game))));
        verify(snapshot(game).equals(snapshot(restored)), "Все данные партии восстановлены");
        verify(game.legalActions().stream().map(Action::id).toList().equals(
                restored.legalActions().stream().map(Action::id).toList()), "Допустимые действия совпадают");
        verify(!game.gameId.equals(restored.gameId), "Память ИИ отделяет загруженную ветку");
        return restored;
    }
    public static void main(String[] args) throws Exception {
        // Старый JSON id/type должен восстанавливать карты с рабочим поведением,
        // а не только совпадающими полями.
        Game allCards = empty();
        allCards.handLimit = 12;
        allCards.board.set(1, PieceFactory.create(3, 'N', true));
        allCards.board.set(2, PieceFactory.create(4, 'B', true));
        allCards.board.set(8, PieceFactory.create(5, 'P', true));
        allCards.board.set(48, PieceFactory.create(6, 'P', false));
        allCards.board.set(57, PieceFactory.create(7, 'N', false));
        allCards.board.get(1).protect();
        allCards.captured.add(PieceFactory.create(8, 'P', true));
        allCards.captured.add(PieceFactory.create(9, 'N', true));
        allCards.nextPieceId = 10;
        allCards.blackHand.add(CardCatalog.create(2000, CardType.SHIELD));
        allCards.deck.add(CardCatalog.create(3000, CardType.FREEZE));
        allCards.deck.add(CardCatalog.create(3001, CardType.SHIELD));
        for (CardType type : CardType.values()) if (type != CardType.UNKNOWN) card(allCards, type);
        allCards.whiteTurn = false;
        move(allCards, 57, 42); // Последний ход фигуры нужен карте отмены.
        allCards.halfMoves = 9;
        for (var held : allCards.whiteHand) {
            Game original = allCards.copy();
            Game loaded = GameSave.decode(JsonCodec.readObject(JsonCodec.toJson(GameSave.encode(original))));
            String id = original.legalActions().stream().filter(a -> a.cardId() == held.id()).findFirst().orElseThrow().id();
            original.play(id); loaded.play(id);
            verify(snapshot(original).equals(snapshot(loaded)), "Эффект карты после загрузки: " + held.type());
            verify(loaded.discard.contains(held) && loaded.whiteHand.stream().noneMatch(c -> c.id() == held.id())
                    && !loaded.whiteTurn, "Расход карты и смена очереди: " + held.type());
            verify(loaded.halfMoves == (held.type() == CardType.SCOUT || held.type() == CardType.EXCHANGE ? 9 : 0),
                    "Счётчик полуходов: " + held.type());
        }
        Game game = chess.game.TestSessions.game("human", 5, "hard");
        move(game, 12, 28);
        game.board.get(1).protect();
        game.board.get(6).freezeUntil(game.ply + 4);
        game.board.get(8).enableBoost();
        game.board.get(57).sendToAstralUntil(game.ply + 3);
        Game restored = roundTrip(game);
        String action = game.legalActions().stream().filter(a -> a.kind().equals("move")).findFirst().orElseThrow().id();
        game.play(action); restored.play(action);
        verify(snapshot(game).equals(snapshot(restored)), "Продолжение даёт ту же позицию");
        game.expandBoard(1, 2, 1, 2);
        roundTrip(game);
        Game scout = chess.game.TestSessions.game("chatgpt", 3, "normal");
        var card = card(scout, CardType.SCOUT);
        scout.startScout(card.id());
        roundTrip(scout);
        game.result = "draw";
        roundTrip(game);
        GameSession session = chess.game.TestSessions.create(position -> "draw");
        long version = ((Number) session.state().get("version")).longValue();
        Map<String, Object> exported = session.exportGame(version);
        Map<String, Object> loaded = session.importGame(version, exported);
        verify(((Number) loaded.get("version")).longValue() == version + 1, "Импорт меняет версию");
        try { session.importGame(version + 1, Map.of("format", 99)); throw new AssertionError("Некорректный импорт принят"); }
        catch (IllegalArgumentException expected) { }
        verify(session.state().equals(loaded), "Ошибочный импорт не меняет партию");
        Path dir = Files.createTempDirectory("chess-save-test-");
        Path file = dir.resolve("saved-game.json");
        try {
            GameSave.write(game, file);
            verify(snapshot(game).equals(snapshot(GameSave.read(file))), "Партия читается с диска");
            GameSave.write(scout, file);
            verify(snapshot(scout).equals(snapshot(GameSave.read(file))), "Повторное сохранение заменяет слот");
            Files.writeString(file, "{broken");
            try { GameSave.read(file); throw new AssertionError("Повреждённый файл принят"); }
            catch (IllegalArgumentException expected) { }
            Files.delete(file);
            try { GameSave.read(file); throw new AssertionError("Отсутствующий файл принят"); }
            catch (IllegalArgumentException expected) { }
        } finally { Files.deleteIfExists(file); Files.delete(dir); }
        System.out.println("Save checks passed: full state, legal actions, continuation, expansion, scouting, disk and invalid files.");
    }
}
