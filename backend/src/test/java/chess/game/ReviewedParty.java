package chess.game;

import chess.pieces.PieceType;
import chess.cards.CardCatalog;
import chess.cards.CardType;
import chess.pieces.PieceFactory;
import chess.serialization.JsonCodec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Обезличенные позиции из разобранной партии, восстановленные по ключам повторений. */
public final class ReviewedParty {
    private ReviewedParty() {}
    private static final Pattern PIECE = Pattern.compile("([PNBRQK])(true|false)(true|false)(true|false)(true|false)(\\d+):(\\d+)");
    private static final Pattern BOOLEAN = Pattern.compile("true|false");
    private static final Map<String, Object> DATA = read();

    private static Map<String, Object> read() {
        try (var stream = ReviewedParty.class.getResourceAsStream("/ai/reviewed-party.json")) {
            if (stream == null) throw new IllegalStateException("Нет фикстуры партии");
            return JsonCodec.readObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    @SuppressWarnings("unchecked")
    public static Game before(int action) {
        var positions = (Map<String, String>)DATA.get("positions");
        String[] parts = positions.get("" + action).split("\\|", -1);
        String[] cells = parts[0].split("/", -1);
        String[] header = cells[0].substring(cells[0].indexOf("]:") + 2).split(":");
        Game game = new Game(GameSettings.parse("chatgpt", 3, "normal"));
        game.setWhiteTurn(Boolean.parseBoolean(header[0]));
        game.setEnPassant(Integer.parseInt(header[1]));
        var flags = BOOLEAN.matcher(header[2]);
        flags.find(); game.castling().set(true, true, Boolean.parseBoolean(flags.group()));
        flags.find(); game.castling().set(true, false, Boolean.parseBoolean(flags.group()));
        flags.find(); game.castling().set(false, true, Boolean.parseBoolean(flags.group()));
        flags.find(); game.castling().set(false, false, Boolean.parseBoolean(flags.group()));
        for (int i = 1; i < cells.length; i++) {
            if (cells[i].isEmpty()) continue;
            var match = PIECE.matcher(cells[i]);
            if (!match.matches()) throw new IllegalArgumentException(cells[i]);
            var piece = game.newPiece(PieceType.fromCode(match.group(1).charAt(0)), Boolean.parseBoolean(match.group(2)));
            if (Boolean.parseBoolean(match.group(3))) piece.markMoved();
            if (Boolean.parseBoolean(match.group(4))) piece.enableBoost();
            if (Boolean.parseBoolean(match.group(5))) piece.protect();
            piece.freezeUntil(Integer.parseInt(match.group(6)));
            piece.sendToAstralUntil(Integer.parseInt(match.group(7)));
            game.board().set(i - 1, piece);
        }
        var lists = List.of(game.hand(true), game.hand(false), game.cardPiles().deck(), game.cardPiles().discard());
        int cardId = 1000;
        for (int i = 0; i < lists.size(); i++)
            for (String type : parts[i + 1].split(","))
                if (!type.isEmpty()) lists.get(i).add(CardCatalog.create(cardId++, CardType.valueOf(type)));
        var captured = Pattern.compile("([PNBRQK])(true|false)").matcher(parts[5]);
        while (captured.find()) game.captured().add(game.newPiece(PieceType.fromCode(captured.group(1).charAt(0)), Boolean.parseBoolean(captured.group(2))));
        game.history().addAll(((List<String>)DATA.get("history")).subList(0, action - 1));
        return game.observationForCurrentPlayer();
    }
}
