package chess.game;

import chess.board.Board;

import chess.cards.Card;
import chess.cards.CardType;
import chess.pieces.Piece;


import java.util.*;

/**
 * Допустимые цели и исполнение заклинаний. Окончательную законность проверяет Game.
 */
final class CardRules {
    private final Game game;
    private final Board board;

    CardRules(Game game) {
        this.game = game; this.board = game.board;
    }

    /**
     * Перебирает цели; исключает астрал и защищённые щитом вражеские фигуры.
     */
    void addActions(List<Action> out, Card c) {
        switch (c.type()) {
            case SCOUT:
                if (game.deck.size() + game.discard.size() >= 2) {
                    out.add(Action.card(c, -1, -1, "keep"));
                    out.add(Action.card(c, -1, -1, "reverse"));
                }
                return;
            case EXCHANGE:
                if (!game.hand(!game.whiteTurn).isEmpty())
                    for (Card own : game.hand(game.whiteTurn))
                        if (own.id() != c.id()) out.add(Action.card(c, -1, own.id(), ""));
                return;
            case REVIVE_PAWN, REVIVE_PIECE:
                var types = new HashSet<Character>();
                for (Piece p : game.captured)
                    if (p.white() == game.whiteTurn && (c.type() == CardType.REVIVE_PAWN ? p.type() == 'P' : p.type() != 'P' && p.type() != 'K'))
                        types.add(p.type());
                for (char type : types)
                    for (int s : game.startingSquares(type, game.whiteTurn))
                        if (board.get(s) == null) out.add(Action.card(c, s, -1, "" + type));
                return;
            default:
                break;
        }
        for (int s = 0; s < board.size(); s++) {
            Piece p = board.get(s);
            if (p == null || p.astral()) continue;
            boolean own = p.white() == game.whiteTurn;
            if (!own && p.shield()) continue;
            boolean yes = switch (c.type()) {
                case PAWN_BOOST -> own && p.type() == 'P' && !p.boost();
                case KING_BOOST -> own && p.type() == 'K' && !p.boost();
                case SHIELD -> own && !p.shield();
                case FREEZE -> !own && p.type() != 'K' && !p.frozen();
                case ASTRAL -> !own && p.type() != 'K';
                case CONVERT -> !own && p.type() == 'P';
                case CLEANSE -> p.boost() || p.shield() || p.frozen();
                default -> false;
            };
            if (yes) out.add(Action.card(c, s, -1, ""));
            if (c.type() == CardType.SWAP && own && p.type() != 'K') for (int t = s + 1; t < board.size(); t++) {
                Piece q = board.get(t);
                if (q != null && !q.astral() && q.white() == game.whiteTurn && q.type() != 'K' && Math.max(Math.abs(board.geometry().rank(t) - board.geometry().rank(s)), Math.abs(board.geometry().file(t) - board.geometry().file(s))) == 1
                        && !(p.type() == 'P' && (board.geometry().rank(t) == 0 || board.geometry().rank(t) == board.height() - 1)) && !(q.type() == 'P' && (board.geometry().rank(s) == 0 || board.geometry().rank(s) == board.height() - 1)))
                    out.add(Action.card(c, s, t, ""));
            }
        }
    }

    /**
     * Расходует карту и применяет эффект. Проверка цели уже выполнена при выборе действия.
     */
    void apply(Action a) {
        var cards = game.hand(game.whiteTurn);
        Card c = cards.stream().filter(x -> x.id() == a.cardId()).findFirst().orElseThrow();
        cards.remove(c);
        game.discard.add(c);
        Piece p = a.to() >= 0 ? board.get(a.to()) : null;
        switch (c.type()) {
            case PAWN_BOOST, KING_BOOST -> p.enableBoost();
            case SHIELD -> p.protect();
            // Эффект заканчивается после следующего действия владельца цели.
            case FREEZE -> p.freezeUntil(game.ply + 2);
            case ASTRAL -> p.sendToAstralUntil(game.ply + 4);
            case CLEANSE -> p.clearEffects();
            case CONVERT -> {
                p.changeSide(game.whiteTurn);
                p.markMoved();
                game.halfMoves = 0;
            }
            case REVIVE_PAWN, REVIVE_PIECE -> {
                char type = a.option().charAt(0);
                Piece old = game.captured.stream().filter(x -> x.white() == game.whiteTurn && x.type() == type).findFirst().orElseThrow();
                game.captured.remove(old);
                board.set(a.to(), new Piece(game.nextPieceId++, type, game.whiteTurn));
                if (type != 'P') board.get(a.to()).markMoved();
                game.halfMoves = 0;
            }
            case SWAP -> {
                Piece q = board.get(a.other());
                game.disableCastle(p, a.to());
                game.disableCastle(q, a.other());
                board.set(a.to(), q);
                board.set(a.other(), p);
                p.consumeMove();
                q.consumeMove();
                game.halfMoves = 0;
            }
            case SCOUT -> {
                game.ensureScoutDeck();
                if (a.option().equals("reverse")) Collections.swap(game.deck, 0, 1);
            }
            case EXCHANGE -> {
                Card own = cards.stream().filter(x -> x.id() == a.other()).findFirst().orElseThrow();
                var enemy = game.hand(!game.whiteTurn);
                // Чужая карта выбирается вслепую, без раскрытия руки соперника.
                int index = java.util.concurrent.ThreadLocalRandom.current().nextInt(enemy.size());
                Card theirs = enemy.set(index, own);
                cards.remove(own);
                cards.add(theirs);
            }
            default -> throw new IllegalArgumentException("Unknown card");
        }
        // Изменяющие доску карты прерывают обычную последовательность для правила пятидесяти ходов.
        if (c.type() != CardType.SCOUT && c.type() != CardType.EXCHANGE) game.halfMoves = 0;

    }

}
