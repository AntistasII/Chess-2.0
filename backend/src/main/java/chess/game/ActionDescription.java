package chess.game;

import chess.cards.Card;
import chess.cards.CardCatalog;
import chess.cards.CardType;

/** Читаемые записи ходов и карт для истории партии. */
final class ActionDescription {
    private ActionDescription() {}

    static String describe(Game game, Action action) {
        return switch (action.kind()) {
            case "draw" -> "взяли карту";
            case "move" -> describeMove(game, action);
            default -> describeCard(game, action);
        };
    }

    private static String describeMove(Game game, Action action) {
        String suffix = action.option().equals("castle") ? " (рокировка)"
                : action.option().isEmpty() ? "" : " = " + action.option();
        return pieceName(game.board.get(action.from()).type()) + " " + game.square(action.from())
                + " → " + game.square(action.to()) + suffix;
    }

    private static String describeCard(Game game, Action action) {
        Card card = game.scouting == action.cardId() ? CardCatalog.create(game.scouting, CardType.SCOUT)
                : game.hand(game.whiteTurn).stream().filter(c -> c.id() == action.cardId()).findFirst().orElseThrow();
        if (card.type() == CardType.HIDDEN_TRAP) return card.name(); // Координаты не раскрываются через историю.
        String target = action.to() >= 0 ? " · " + game.square(action.to()) : "";
        String other = action.other() >= 0 && (card.type() == CardType.SWAP || card.type() == CardType.SACRIFICE) ? " ↔ " + game.square(action.other()) : "";
        return card.name() + target + other;
    }

    private static String pieceName(char type) {
        return switch (type) {
            case 'P' -> "Пешка";
            case 'N' -> "Конь";
            case 'B' -> "Слон";
            case 'R' -> "Ладья";
            case 'Q' -> "Ферзь";
            case 'K' -> "Король";
            default -> "Фигура";
        };
    }
}
