package chess.game;

import chess.cards.Card;
import chess.cards.CardCatalog;
import chess.cards.CardType;
import chess.cards.impl.ScoutCard;
import chess.pieces.Piece;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Допустимые действия, исполнение действия и завершение полухода. */
final class TurnRules {
    private final Game game;

    TurnRules(Game game) { this.game = game; }

    List<Action> legalActions() { return legalActions(action -> true); }

    List<Action> legalActions(Predicate<Action> filter) {
        var out = new ArrayList<Action>();
        for (var transition : legalTransitions(filter)) out.add(transition.action());
        return out;
    }

    /** Кандидаты, после которых собственный король не под шахом, вместе с итоговыми позициями. */
    List<Game.Transition> legalTransitions(Predicate<Action> filter) {
        if (game.result().finished()) return List.of();
        var out = new ArrayList<Game.Transition>();
        boolean actor = game.whiteTurn();
        for (Action action : candidateActions()) {
            if (!filter.test(action)) continue;
            Game next = game.simulate(action);
            if (!next.inCheck(actor)) out.add(new Game.Transition(action, next));
        }
        return out;
    }

    /** Все действия по правилам фигур и карт до проверки безопасности короля. */
    private List<Action> candidateActions() {
        if (game.scoutingActive()) {
            Card scout = CardCatalog.create(game.scouting(), CardType.SCOUT);
            return List.of(Action.card(scout, -1, -1, ScoutCard.KEEP), Action.card(scout, -1, -1, ScoutCard.REVERSE));
        }
        boolean white = game.whiteTurn();
        var piles = game.cardPiles();
        var out = new ArrayList<>(new MoveRules(game).moves());
        // При шахе брать карту нельзя: ход или карта должны сразу устранить шах.
        if (piles.canDraw(white) && !game.inCheck(white))
            out.add(Action.draw());
        var cardRules = new CardRules(game);
        for (Card card : piles.hand(white)) cardRules.addActions(out, card);
        return out;
    }

    void startScout(int cardId) {
        if (game.scoutingActive() || game.legalActions().stream()
                .noneMatch(a -> a.cardId() == cardId && a.option().equals(ScoutCard.KEEP)))
            throw new IllegalArgumentException("Разведка недоступна");
        Card card = game.cardInHand(cardId);
        if (card.type() != CardType.SCOUT) throw new IllegalArgumentException("Разведка недоступна");
        game.cardPiles().prepareScout();
        game.cardPiles().spend(game.whiteTurn(), cardId);
        game.setScouting(cardId);
        game.setVersion(game.version() + 1);
    }

    void applyUnchecked(Action action) {
        // В расчётных копиях снимок отмены нужен лишь тому, кто ходит следующим и держит эту карту.
        MoveUndo nextUndo = action.isMove() && (!game.simulation() || game.holds(!game.whiteTurn(), CardType.UNDO_MOVE))
                ? MoveUndo.before(game, action) : null;
        var positionsBefore = game.effects().isEmpty() ? null : piecePositions();
        int oldEnPassant = game.enPassant();
        game.setEnPassant(-1);
        if (game.scoutingActive()) {
            if (action.option().equals(ScoutCard.REVERSE)) Collections.swap(game.cardPiles().deck(), 0, 1);
            game.setScouting(-1);
        } else if (action.isDraw()) {
            game.cardPiles().draw(game.whiteTurn());
        } else if (action.isMove()) {
            new MoveRules(game).apply(action, oldEnPassant);
        } else {
            new CardRules(game).apply(action);
        }
        game.setLastMove(nextUndo);
        if (positionsBefore != null) springTraps(positionsBefore);
        game.advanceTurn();
    }

    /** Положение фигур в координатах исходной доски: номер клетки меняется при расширении. */
    private HashMap<Integer, String> piecePositions() {
        var positions = new HashMap<Integer, String>();
        var board = game.board();
        for (int square = 0; square < board.size(); square++) {
            Piece p = board.get(square);
            if (p != null) positions.put(p.id(), originCoordinate(square));
        }
        return positions;
    }

    private String originCoordinate(int square) {
        var geometry = game.boardGeometry();
        return (geometry.file(square) - geometry.originFile()) + ":" + (geometry.rank(square) - geometry.originRank());
    }

    /** Любое появление фигуры на капкане, включая рокировку, перестановку и возрождение. */
    private void springTraps(HashMap<Integer, String> positionsBefore) {
        for (var entry : game.effects().sorted().entrySet()) {
            Piece p = game.board().get(entry.getKey());
            SquareEffect effect = entry.getValue();
            if (p == null || effect.blocksEntry() || Objects.equals(positionsBefore.get(p.id()), originCoordinate(entry.getKey())))
                continue;
            int until = game.ply() + 2 * effect.freezeTurns() + (p.white() == game.whiteTurn() ? 1 : 0);
            p.freezeUntil(Math.max(p.frozenUntil(), until));
        }
    }
}
