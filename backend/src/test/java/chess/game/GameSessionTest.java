package chess.game;

import chess.ai.Ai;
import chess.cards.Card;
import chess.cards.CardType;
import chess.pieces.Piece;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/** Проверки сессии, конкурентных команд и скрытия данных в API. */
public final class GameSessionTest {
    private static int checks;

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }

    private static long version(Map<String, Object> state) {
        return ((Number) state.get("version")).longValue();
    }

    public static void main(String[] args) throws Exception {
        Game network = new Game("network", 3, "normal");
        network.whiteHand.add(new Card(1000, CardType.SCOUT));
        network.blackHand.add(new Card(1001, CardType.SHIELD));
        GameSession networkSession = new GameSession(network, g -> { throw new AssertionError("No network AI"); });
        networkSession.scout(0, 1000);
        check(((List<?>)networkSession.playerState(true).get("scouting")).size() == 2, "Owner sees scout cards");
        check(((List<?>)networkSession.playerState(false).get("scouting")).isEmpty(), "Remote player cannot see opponent scouting");
        check(((List<?>)networkSession.playerState(false).get("actions")).isEmpty(), "Remote player cannot see scout action IDs");
        String scoutChoice = network.legalActions().get(0).id();
        networkSession.play(network.version, scoutChoice);
        check(((List<?>)networkSession.playerState(false).get("hand")).size() == 1, "Black hand remains visible on black turn");
        check(((List<?>)networkSession.playerState(true).get("hand")).isEmpty(), "Host view does not switch to black hand");
        check(GameSave.decode(GameSave.encode(network)).mode.equals("network"), "Save preserves network mode");
        GameSession session = new GameSession(Ai::choose);
        Map<String, Object> initial = session.state();
        check(initial.keySet().equals(Set.of("board", "boardSize", "whiteTurn", "mode", "difficulty", "handLimit",
                "version", "ply", "hand", "enemyCards", "handWhite", "deckCount", "discardCount", "check",
                "result", "canClaimDraw", "actions", "captured", "history", "scouting")), "Public API schema preserved");
        check(((List<?>) initial.get("board")).size() == 32, "Initial board projection");
        try {
            session.newGame("invalid", 3, "normal");
            throw new AssertionError("Invalid settings accepted");
        } catch (IllegalArgumentException expected) {
            check(version(session.state()) == version(initial), "Invalid restart leaves the party untouched");
        }

        Map<String, Object> human = session.newGame("human", 5, "normal");
        check(version(human) > version(initial), "New party receives a new revision");
        Map<String, Object> afterDraw = session.play(version(human), "draw");
        check(((List<?>) human.get("history")).isEmpty(), "Old history snapshot is stable after a command");
        check(((List<?>) afterDraw.get("history")).size() == 1, "New history contains the command");
        check(((List<?>) afterDraw.get("hand")).isEmpty() && (int) afterDraw.get("enemyCards") == 1,
                "Hotseat projection hides the previous player's card");
        try {
            session.play(version(human), "draw");
            throw new AssertionError("Stale command accepted");
        } catch (GameSession.StalePositionException expected) {
            check(version(session.state()) == version(afterDraw), "Stale command cannot mutate the party");
        }

        // Из двух одновременных команд с одной версией принимается только одна.
        long revision = version(afterDraw);
        Callable<Boolean> draw = () -> {
            try { session.play(revision, "draw"); return true; }
            catch (GameSession.StalePositionException expected) { return false; }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(draw, draw));
            check(results.get(0).get() != results.get(1).get(), "Only one concurrent command commits");
        }
        check(version(session.state()) == revision + 1, "Concurrent command increments revision once");

        Map<String, Object> ai = session.newGame("ai", 3, "easy");
        try {
            session.runAi(version(ai));
            throw new AssertionError("AI moved on the human turn");
        } catch (IllegalArgumentException expected) {
            check(version(session.state()) == version(ai), "AI cannot act on human turn");
        }

        Game game = new Game("ai", 3, "normal");
        game.whiteHand.add(new Card(101, CardType.SHIELD));
        game.blackHand.add(new Card(102, CardType.CONVERT));
        Game observation = game.observationForCurrentPlayer();
        check(observation.whiteHand.get(0).type() == CardType.SHIELD, "AI retains its own known cards");
        check(observation.blackHand.get(0).type() == CardType.UNKNOWN, "AI observation masks enemy card identities");
        check(observation.deck.stream().allMatch(c -> c.type() == CardType.UNKNOWN), "AI observation masks deck order and identities");
        check(game.blackHand.get(0).type() == CardType.CONVERT && game.deck.stream().noneMatch(c -> c.type() == CardType.UNKNOWN),
                "Masking does not change the live party");
        // Поиск должен сохранить все эффекты на копии и не менять исходную фигуру.
        Piece piece = game.board.get(8);
        piece.enableBoost();
        piece.protect();
        piece.freezeUntil(4);
        piece.sendToAstralUntil(6);
        Game search = game.copy();
        Piece copied = search.board.get(8);
        check(copied != piece && copied.id() == piece.id() && copied.boost() && copied.shield()
                && copied.frozenUntil() == 4 && copied.astralUntil() == 6, "Search preserves all piece effects");
        copied.clearEffects();
        check(piece.boost() && piece.shield() && piece.frozenUntil() == 4 && piece.astralUntil() == 6,
                "Clearing search effects cannot mutate live effects");
        piece.clearEffects();

        // При удалении карты из коллекции важны её значения, как и до выделения класса.
        Card shield = new Card(500, CardType.SHIELD);
        var cards = new java.util.ArrayList<>(List.of(shield, new Card(501, CardType.SHIELD)));
        check(cards.remove(new Card(500, CardType.SHIELD)) && cards.size() == 1
                && cards.get(0).id() == 501, "Card removal distinguishes copies of the same spell");
        check(shield.hashCode() == new Card(500, CardType.SHIELD).hashCode()
                && shield.name().equals("Щит"), "Card value contract and public name preserved");

        observation.board.get(8).enableBoost();
        check(!game.board.get(8).boost(), "Search pieces are isolated from live pieces");

        // Публичное чтение через границы пакетов не даёт менять живую доску или руку.
        Piece externalPiece = game.pieceAt(8);
        externalPiece.protect();
        check(!game.board.get(8).shield(), "External piece view cannot mutate the live board");
        try {
            game.cards(true).clear();
            throw new AssertionError("External caller changed the live hand");
        } catch (UnsupportedOperationException expected) {
            check(game.whiteHand.size() == 1, "Public card list is immutable");
        }
        Game simulated = game.simulate(game.legalActions().stream()
                .filter(a -> a.from() == 12 && a.to() == 28).findFirst().orElseThrow());
        check(simulated.board.get(28) != null && game.board.get(28) == null && game.whiteTurn
                && game.version == 0, "Public simulation leaves the live party untouched");

        // Сессия подключает произвольную стратегию и скрывает данные до её вызова.
        game.whiteTurn = false;
        GameSession plugged = new GameSession(game, position -> {
            check(!position.whiteTurn(), "Injected strategy receives the current side");
            check(position.cards(true).get(0).type() == CardType.UNKNOWN
                    && position.cards(false).get(0).type() == CardType.CONVERT,
                    "Session hides enemy cards before invoking a strategy");
            // Даже команда на переданной копии не меняет настоящую партию.
            position.play("draw");
            return "draw";
        });
        plugged.runAi(game.version);
        check(game.ply == 1 && game.whiteTurn && game.blackHand.size() == 2
                && game.whiteHand.get(0).type() == CardType.SHIELD,
                "Injected strategy cannot apply an extra live turn");

        // ChatGPT видит свою руку, но не карты человека; браузер получает обратное представление.
        Game remote = new Game("chatgpt", 3, "normal");
        remote.whiteHand.add(new Card(901, CardType.SHIELD));
        remote.blackHand.add(new Card(902, CardType.SCOUT));
        remote.whiteTurn = false;
        GameSession remoteSession = new GameSession(remote, p -> { throw new AssertionError(); }, p -> {
            check(p.cards(true).get(0).type() == CardType.UNKNOWN, "ChatGPT cannot see human cards");
            var view = p.opponentState();
            check(view.get("handWhite").equals(false) && ((List<?>) view.get("hand")).size() == (p.scouting >= 0 ? 0 : 1),
                    "ChatGPT receives its own hand");
            if (((List<?>) view.get("scouting")).isEmpty())
                return p.legalActions().stream().filter(a -> a.cardId() == 902).findFirst().orElseThrow().id();
            check(((List<?>) view.get("scouting")).stream().noneMatch(c -> ((Map<?, ?>) c).get("type").equals("UNKNOWN")),
                    "ChatGPT scout reveals top two cards");
            check(p.deck.subList(2, p.deck.size()).stream().allMatch(c -> c.type() == CardType.UNKNOWN),
                    "Scout does not reveal rest of deck");
            return p.legalActions().get(0).id();
        });
        check(((List<?>) remoteSession.state().get("actions")).isEmpty(), "Human cannot see opponent legal card actions");
        try { remoteSession.play(remote.version, "draw"); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { check(remote.ply == 0, "Human cannot move for ChatGPT"); }
        remoteSession.runAi(remote.version);
        check(remote.scouting == 902 && !remote.whiteTurn && remote.ply == 0, "Scout commits before revealing cards");
        check(((List<?>) remoteSession.state().get("scouting")).isEmpty(), "Scout is hidden from human browser");
        remoteSession.runAi(remote.version);
        check(remote.whiteTurn && remote.ply == 1 && remote.scouting < 0, "Scout finishes a single opponent turn");

        Game failing = new Game("chatgpt", 3, "normal");
        failing.whiteTurn = false;
        GameSession failingSession = new GameSession(failing, p -> "draw", p -> "invalid");
        try { failingSession.runAi(0); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { check(failing.version == 0 && failing.ply == 0, "Invalid model answer cannot mutate game"); }

        // Долгий запрос не блокирует новую партию; ответ старой партии отбрасывается.
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        Game pending = new Game("chatgpt", 3, "normal");
        pending.whiteTurn = false;
        GameSession pendingSession = new GameSession(pending, p -> "draw", p -> {
            started.countDown();
            try { release.await(); } catch (InterruptedException e) { throw new RuntimeException(e); }
            return "draw";
        });
        try (var pool = Executors.newSingleThreadExecutor()) {
            var future = pool.submit(() -> pendingSession.runAi(0));
            started.await();
            check(version(pendingSession.state()) == 0, "State is readable while opponent waits");
            try { pendingSession.runAi(0); throw new AssertionError(); }
            catch (IllegalArgumentException expected) { check(true, "Duplicate model request rejected"); }
            pendingSession.newGame("human", 3, "normal");
            release.countDown();
            try { future.get(); throw new AssertionError(); }
            catch (java.util.concurrent.ExecutionException e) {
                check(e.getCause() instanceof GameSession.StalePositionException, "Late model answer rejected");
            }
            check(pendingSession.state().get("ply").equals(0), "Late response cannot change new party");
        }

        System.out.println("Passed " + checks + " session and API projection checks.");
    }
}
