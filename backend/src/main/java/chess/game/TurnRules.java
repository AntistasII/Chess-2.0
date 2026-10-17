package chess.game;

import chess.cards.Card;
import chess.cards.CardCatalog;
import chess.cards.CardType;
import chess.pieces.Piece;
import java.util.*;

/** Допустимые действия, применение карт и завершение полухода. */
final class TurnRules {
    private final Game game;

    TurnRules(Game game) { this.game = game; }

    List<Action> legalActions() { return legalActions(action -> true); }

    List<Action> legalActions(java.util.function.Predicate<Action> filter) {
        if (!game.result.isEmpty()) return List.of();
        var out = new ArrayList<Action>();
        boolean actor = game.whiteTurn;
        for (Action action : pseudoActions())
            if (filter.test(action) && !game.simulate(action).inCheck(actor)) out.add(action);
        return out;
    }

    private List<Action> pseudoActions() {
        if (game.scouting >= 0) {
            Card c = CardCatalog.create(game.scouting, CardType.SCOUT);
            return List.of(Action.card(c, -1, -1, "keep"), Action.card(c, -1, -1, "reverse"));
        }
        var out = new ArrayList<>(new MoveRules(game).moves());
        if (game.hand(game.whiteTurn).size() < game.handLimit && (!game.deck.isEmpty() || !game.discard.isEmpty()) && !game.inCheck(game.whiteTurn))
            out.add(Action.draw());
        var rules = new CardRules(game);
        for (Card card : game.hand(game.whiteTurn)) rules.addActions(out, card);
        return out;
    }

    void startScout(int cardId) {
        if (game.scouting >= 0 || game.legalActions().stream().noneMatch(a -> a.cardId() == cardId && a.option().equals("keep")))
            throw new IllegalArgumentException("Разведка недоступна");
        Card c = game.hand(game.whiteTurn).stream().filter(x -> x.id() == cardId && x.type() == CardType.SCOUT).findFirst().orElseThrow();
        ensureScoutDeck();
        game.hand(game.whiteTurn).remove(c);
        game.discard.add(c);
        game.scouting = cardId;
        game.version++;
    }

    void applyUnchecked(Action a) {
        MoveUndo nextUndo = a.kind().equals("move") ? MoveUndo.before(game, a) : null;
        var positionsBefore = new HashMap<Integer, String>();
        var oldGeometry = game.boardGeometry();
        if (!game.squareEffects.isEmpty()) for (int square = 0; square < game.board.size(); square++) {
            Piece p = game.board.get(square);
            if (p != null) positionsBefore.put(p.id(), (oldGeometry.file(square) - oldGeometry.originFile())
                    + ":" + (oldGeometry.rank(square) - oldGeometry.originRank()));
        }
        int oldEp = game.enPassant;
        game.enPassant = -1;
        if (game.scouting >= 0) {
            if (a.option().equals("reverse")) Collections.swap(game.deck, 0, 1);
            game.scouting = -1;
        } else if (a.kind().equals("draw")) {
            ensureDeck();
            game.hand(game.whiteTurn).add(game.deck.remove(0));
        } else if (a.kind().equals("move")) {
            new MoveRules(game).apply(a, oldEp);
        } else {
            new CardRules(game).apply(a);
        }
        game.lastMove = nextUndo;
        // Любое появление фигуры на капкане, включая рокировку, перестановку и возрождение.
        for (var entry : game.squareEffects.entrySet()) {
            Piece p = game.board.get(entry.getKey());
            SquareEffect effect = entry.getValue();
            var geometry = game.boardGeometry();
            String coordinate = (geometry.file(entry.getKey()) - geometry.originFile()) + ":"
                    + (geometry.rank(entry.getKey()) - geometry.originRank());
            if (p != null && !effect.blocksEntry() && !Objects.equals(positionsBefore.get(p.id()), coordinate)) {
                int until = game.ply + 2 * effect.freezeTurns() + (p.white() == game.whiteTurn ? 1 : 0);
                p.freezeUntil(Math.max(p.frozenUntil(), until));
            }
        }
        finishTurn();
    }

    /** Смена стороны и окончание эффектов одинаковы для хода, карты и добора. */
    private void finishTurn() {
        game.ply++;
        game.whiteTurn = !game.whiteTurn;
        game.squareEffects.values().removeIf(effect -> effect.expiresAt() <= game.ply);
        for (Piece piece : game.board)
            if (piece != null) piece.expireEffects(game.ply);
    }

    private void ensureDeck() {
        if (game.deck.isEmpty()) {
            game.deck.addAll(game.discard);
            game.discard.clear();
            Collections.shuffle(game.deck);
        }
    }

    void ensureScoutDeck() {
        if (game.deck.size() < 2 && !game.discard.isEmpty()) {
            Collections.shuffle(game.discard);
            game.deck.addAll(game.discard);
            game.discard.clear();
        }
    }
}
