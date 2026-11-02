package chess.game;

import chess.cards.Card;
import chess.pieces.Piece;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Представление партии для интерфейса (JSON): скрывает руку соперника, порядок колоды
 * и невидимые капканы. Набор ключей — контракт с фронтендом и Android.
 */
final class GameView {
    /** Сколько последних записей истории показывает интерфейс. */
    private static final int HISTORY_LINES = 14;

    private GameView() {
    }

    /**
     * Показывает руку текущего игрока в игре вдвоём или белых в игре с соперником.
     */
    static Map<String, Object> of(Game game) {
        boolean shared = game.mode().sharedScreen();
        return of(game, !shared || game.whiteTurn(), shared || game.whiteTurn());
    }

    /**
     * @param viewerWhite    чья рука и какие поля видны
     * @param includeActions добавить список допустимых действий (для игрока, чья очередь)
     */
    static Map<String, Object> of(Game game, boolean viewerWhite, boolean includeActions) {
        var piles = game.cardPiles();
        var out = new LinkedHashMap<String, Object>();
        out.put("board", pieces(game));
        out.put("squareEffects", squareEffects(game, viewerWhite));
        var geometry = game.boardGeometry();
        out.put("boardSize", Map.of("width", geometry.width(), "height", geometry.height(),
                "originFile", geometry.originFile(), "originRank", geometry.originRank()));
        out.put("whiteTurn", game.whiteTurn());
        out.put("castlingRights", Map.of("white", List.of(game.hasCastlingRight(true, true), game.hasCastlingRight(true, false)),
                "black", List.of(game.hasCastlingRight(false, true), game.hasCastlingRight(false, false))));
        out.put("mode", game.mode().id());
        out.put("difficulty", game.difficulty().id());
        out.put("handLimit", game.handLimit(viewerWhite));
        out.put("enemyHandLimit", game.handLimit(!viewerWhite));
        out.put("version", game.version());
        out.put("gameId", game.gameId());
        out.put("ply", game.ply());
        out.put("hand", piles.hand(viewerWhite).stream().map(GameView::cardJson).toList());
        out.put("enemyCards", piles.hand(!viewerWhite).size());
        out.put("handWhite", viewerWhite);
        out.put("deckCount", piles.deck().size());
        out.put("discardCount", piles.discard().size());
        out.put("check", game.inCheck(game.whiteTurn()));
        out.put("result", game.result().id());
        out.put("canClaimDraw", game.canClaimDraw());
        out.put("actions", includeActions ? game.legalActions().stream().map(GameView::actionJson).toList() : List.of());
        out.put("captured", game.captured().stream()
                .map(p -> Map.of("type", String.valueOf(p.type().code()), "white", p.white())).toList());
        var history = game.history();
        out.put("history", List.copyOf(history.subList(Math.max(0, history.size() - HISTORY_LINES), history.size())));
        // Разведка раскрывает две верхние карты только тому, кто её применил.
        out.put("scouting", game.scoutingActive() && viewerWhite == game.whiteTurn()
                ? piles.deck().subList(0, 2).stream().map(GameView::cardJson).toList() : List.of());
        return out;
    }

    private static List<Object> pieces(Game game) {
        var board = game.board();
        int ply = game.ply();
        var pieces = new ArrayList<Object>();
        for (int s = 0; s < board.size(); s++) {
            Piece p = board.get(s);
            if (p != null)
                pieces.add(Map.of("square", s, "id", p.id(), "type", String.valueOf(p.type().code()), "white", p.white(),
                        "boost", p.boost(), "shield", p.shield(), "frozen", p.frozen(), "astral", p.astral(),
                        "frozenTurns", turnsLeft(p.frozenUntil(), ply), "astralTurns", turnsLeft(p.astralUntil(), ply)));
        }
        return pieces;
    }

    private static List<Object> squareEffects(Game game, boolean viewerWhite) {
        int ply = game.ply();
        var out = new ArrayList<Object>();
        for (var e : game.effects().sorted().entrySet()) {
            SquareEffect effect = e.getValue();
            if (!effect.visibleTo(viewerWhite)) continue;
            out.add(Map.of("square", e.getKey(), "type", effect.type().name(), "white", effect.white(),
                    "turns", turnsLeft(effect.expiresAt(), ply), "freezeTurns", effect.freezeTurns()));
        }
        return out;
    }

    /** Оставшиеся ходы владельца: один ход — два полухода партии. */
    private static int turnsLeft(int untilPly, int ply) {
        return Math.max(0, (untilPly - ply + 1) / 2);
    }

    /**
     * Поля карты, используемые интерфейсом.
     */
    static Map<String, Object> cardJson(Card c) {
        return Map.of("id", c.id(), "type", c.type().toString(), "name", c.name(), "durationTurns", c.durationTurns(), "freezeTurns", c.freezeTurns());
    }

    /**
     * Поля доступного действия; клиент отправляет его идентификатор.
     */
    static Map<String, Object> actionJson(Action a) {
        return Map.of("id", a.id(), "kind", a.kind().id(), "from", a.from(), "to", a.to(), "other", a.other(),
                "cardId", a.cardId(), "option", a.option());
    }
}
