package chess.ai;

import chess.game.Action;
import chess.game.Game;

/** Мягкая цена потери темпа: не запрещает вынужденные отступления и тактические решения. */
final class MovePreferences {
    private MovePreferences() {}

    static double castlingLossPenalty(Game position, Action action, Game next) {
        boolean side = position.whiteTurn();
        if (!action.kind().equals("move") || action.option().equals("castle") || position.inCheck(side)
                || next.inCheck(!side) || position.pieceAt(action.to()) != null) return 0;
        // В окончании активность короля важнее сохранения рокировки.
        if (KingSafety.attackingMaterial(position, !side) < 900) return 0;
        boolean before = position.hasCastlingRight(side, true) || position.hasCastlingRight(side, false);
        boolean after = next.hasCastlingRight(side, true) || next.hasCastlingRight(side, false);
        return before && !after ? 90 : 0;
    }

    static double repeatedActionPenalty(Game position, Action action) {
        if (!EndgameConversion.active(position, position.whiteTurn()) || !action.kind().equals("move")
                || !action.option().isEmpty() || position.inCheck(position.whiteTurn())) return 0;
        String name = switch(position.pieceAt(action.from()).type()) {
            case 'K' -> "Король"; case 'Q' -> "Ферзь"; case 'R' -> "Ладья";
            case 'B' -> "Слон"; case 'N' -> "Конь"; default -> "Пешка";
        };
        String label=(position.whiteTurn()?"Белые: ":"Чёрные: ")+name+" "
                +position.boardGeometry().square(action.from())+" → "+position.boardGeometry().square(action.to());
        var history=position.moveHistory();int repeats=0;
        for(int i=history.size()-1;i>=Math.max(0,history.size()-32);i--) {
            String line=history.get(i);
            if(line.contains(": Увеличить "))break;
            if(line.equals(label))repeats++;
        }
        return repeats*90;
    }

    static double reversalPenalty(Game position, Action action, Game next) {
        boolean conversion = EndgameConversion.active(position, position.whiteTurn());
        if (!action.kind().equals("move") || !action.option().isEmpty()
                || position.inCheck(position.whiteTurn()) || !conversion && next.inCheck(!position.whiteTurn())
                || position.pieceAt(action.to()) != null) return 0;
        var piece = position.pieceAt(action.from());
        String name = switch (piece.type()) {
            case 'N' -> "Конь"; case 'B' -> "Слон";
            case 'K' -> conversion ? "Король" : "";
            case 'Q' -> conversion ? "Ферзь" : "";
            case 'R' -> conversion ? "Ладья" : "";
            default -> "";
        };
        if (name.isEmpty()) return 0;
        var geometry = position.boardGeometry();
        String own = position.whiteTurn() ? "Белые: " : "Чёрные: ";
        String previous = own + name + " " + geometry.square(action.to()) + " → " + geometry.square(action.from());
        var history = position.moveHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            String line = history.get(i);
            // После вставки линии прежние координаты истории уже не описывают текущую сетку.
            if (line.contains(": Увеличить ")) return 0;
            if (line.startsWith(own)) return line.equals(previous) ? 90 : 0;
        }
        return 0;
    }
}
