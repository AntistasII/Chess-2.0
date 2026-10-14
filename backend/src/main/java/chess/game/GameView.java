package chess.game;

import chess.cards.Card;
import chess.pieces.Piece;

import java.util.*;

/**
 * Представление партии для API: скрывает руку соперника и порядок карт в колоде.
 */
final class GameView {
    private GameView() {
    }

    /**
     * Показывает руку текущего игрока в игре вдвоём или белых в игре с ИИ.
     */
    static Map<String, Object> of(Game game) {
        return of(game, game.mode.equals("human") ? game.whiteTurn : true,
                game.mode.equals("human") || game.whiteTurn);
    }

    static Map<String, Object> of(Game game, boolean visible, boolean includeActions) {
        var out = new LinkedHashMap<String, Object>();
        var pieces = new ArrayList<Object>();
        for (int s = 0; s < game.board.size(); s++) {
            Piece p = game.board.get(s);
            if (p != null)
                pieces.add(Map.of("square", s, "id", p.id(), "type", "" + p.type(), "white", p.white(), "boost", p.boost(), "shield", p.shield(), "frozen", p.frozen(), "astral", p.astral(), "astralTurns", Math.max(0, (p.astralUntil() - game.ply + 1) / 2)));
        }
        out.put("board", pieces);
        var geometry = game.boardGeometry();
        out.put("boardSize", Map.of("width", geometry.width(), "height", geometry.height(),
                "originFile", geometry.originFile(), "originRank", geometry.originRank()));
        out.put("whiteTurn", game.whiteTurn);
        out.put("mode", game.mode);
        out.put("difficulty", game.difficulty);
        out.put("handLimit", game.handLimit);
        out.put("version", game.version);
        out.put("ply", game.ply);
        out.put("hand", game.hand(visible).stream().map(GameView::cardJson).toList());
        out.put("enemyCards", game.hand(!visible).size());
        out.put("handWhite", visible);
        out.put("deckCount", game.deck.size());
        out.put("discardCount", game.discard.size());
        out.put("check", game.inCheck(game.whiteTurn));
        out.put("result", game.result);
        out.put("canClaimDraw", game.canClaimDraw());
        out.put("actions", !includeActions ? List.of() : game.legalActions().stream().map(GameView::actionJson).toList());
        out.put("captured", game.captured.stream().map(p -> Map.of("type", "" + p.type(), "white", p.white())).toList());
        out.put("history", List.copyOf(game.history.subList(Math.max(0, game.history.size() - 14), game.history.size())));
        out.put("scouting", game.scouting >= 0 && visible == game.whiteTurn ? game.deck.subList(0, 2).stream().map(GameView::cardJson).toList() : List.of());
        return out;
    }

    /**
     * Поля карты, используемые интерфейсом.
     */
    static Map<String, Object> cardJson(Card c) {
        return Map.of("id", c.id(), "type", c.type().toString(), "name", c.name());
    }

    /**
     * Поля доступного действия; клиент отправляет его идентификатор.
     */
    static Map<String, Object> actionJson(Action a) {
        return Map.of("id", a.id(), "kind", a.kind(), "from", a.from(), "to", a.to(), "other", a.other(), "cardId", a.cardId(), "option", a.option());

    }
}
