package chess.game;

import chess.board.Board;
import chess.cards.*;
import chess.pieces.Piece;
import chess.pieces.PieceFactory;
import chess.pieces.PieceType;
import chess.serialization.JsonCodec;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/**
 * Полный локальный снимок партии. Не используется в публичном представлении API.
 */
final class GameSave {
    /** Формат 1: списки значений в фиксированном порядке; порядок полей не менять. */
    static Map<String, Object> encode(Game g) {
        var out = new LinkedHashMap<String, Object>();
        out.put("format", 1);
        var geo = g.boardGeometry();
        out.put("geometry", List.of(geo.width(), geo.height(), geo.originFile(), geo.originRank()));
        var board = g.board();
        var pieces = new ArrayList<Object>();
        for (int s = 0; s < board.size(); s++)
            if (board.get(s) != null) pieces.add(List.of(s, piece(board.get(s))));
        out.put("board", pieces);
        out.put("squareEffects", g.effects().sorted().entrySet().stream()
                .map(e -> List.of(e.getKey(), e.getValue().type().name(), e.getValue().white(),
                        e.getValue().expiresAt(), e.getValue().freezeTurns())).toList());
        if (g.lastMove() != null) out.put("lastMove", encodeUndo(g.lastMove()));
        out.put("captured", g.captured().stream().map(GameSave::piece).toList());
        var piles = g.cardPiles();
        out.put("cards", List.of(cards(piles.hand(true)), cards(piles.hand(false)), cards(piles.deck()), cards(piles.discard())));
        out.put("history", List.copyOf(g.history()));
        out.put("repetitions", Map.copyOf(g.repetitions()));
        out.put("settings", List.of(g.mode().id(), g.settings().handLimit(), g.difficulty().id()));
        // Необязательное поле: старые сохранения без него используют начальный лимит.
        out.put("handLimits", List.of(g.handLimit(true), g.handLimit(false)));
        out.put("counters", List.of(g.ply(), g.nextPieceId(), g.enPassant(), g.halfMoves(), g.scouting()));
        var castling = g.castling();
        out.put("flags", List.of(g.whiteTurn(), castling.allowed(true, true), castling.allowed(true, false),
                castling.allowed(false, true), castling.allowed(false, false)));
        out.put("result", g.result().id());
        out.put("gameId", g.gameId());
        return out;
    }

    private static Map<String, Object> encodeUndo(MoveUndo undo) {
        var rows = new ArrayList<Object>();
        for (int square = 0; square < undo.board().size(); square++)
            if (undo.board().get(square) != null) rows.add(List.of(square, piece(undo.board().get(square))));
        return Map.of("board", rows, "captured", undo.captured().stream().map(GameSave::piece).toList(),
                "counters", List.of(undo.from(), undo.to(), undo.enPassant(), undo.halfMoves()),
                "flags", List.of(undo.white(), undo.wk(), undo.wq(), undo.bk(), undo.bq()));
    }

    private static MoveUndo decodeUndo(Map<?, ?> data, Game game) {
        Board board = game.board().copy();
        board.clear();
        for (Object o : (List<?>)data.get("board")) {
            var row = (List<?>)o;
            int square = n(row.get(0));
            if (board.get(square) != null) throw new IllegalArgumentException();
            board.set(square, readPiece((List<?>)row.get(1)));
        }
        var captured = ((List<?>)data.get("captured")).stream().map(o -> readPiece((List<?>)o)).toList();
        var counters = (List<?>)data.get("counters");
        var flags = (List<?>)data.get("flags");
        int from = n(counters.get(0)), to = n(counters.get(1)), ep = n(counters.get(2));
        board.geometry().rank(to);
        if (board.get(from) == null || ep < -1 || ep >= board.size() || (Boolean)flags.get(0) == game.whiteTurn())
            throw new IllegalArgumentException();
        return new MoveUndo(board, captured, from, to, (Boolean)flags.get(0), ep, n(counters.get(3)),
                (Boolean)flags.get(1), (Boolean)flags.get(2), (Boolean)flags.get(3), (Boolean)flags.get(4));
    }

    private static List<Object> piece(Piece p) {
        return List.of(p.id(), String.valueOf(p.type().code()), p.white(), p.moved(), p.boost(), p.shield(), p.frozenUntil(), p.astralUntil());
    }

    private static List<Object> cards(List<Card> cards) {
        return cards.stream().map(c -> (Object) List.of(c.id(), c.type().name())).toList();
    }

    private static int n(Object o) {
        return Math.toIntExact(((Number) o).longValue());
    }

    private static Piece readPiece(List<?> row) {
        String type = (String) row.get(1);
        Piece p = PieceFactory.create(n(row.get(0)), PieceType.fromCode(type), (Boolean) row.get(2));
        if ((Boolean) row.get(3)) p.markMoved();
        if ((Boolean) row.get(4)) p.enableBoost();
        if ((Boolean) row.get(5)) p.protect();
        p.freezeUntil(n(row.get(6)));
        p.sendToAstralUntil(n(row.get(7)));
        return p;
    }

    private static List<Card> readCards(List<?> rows) {
        var cards = new ArrayList<Card>();
        for (Object o : rows) {
            var row = (List<?>) o;
            CardType type = CardType.valueOf((String) row.get(1));
            if (type == CardType.UNKNOWN) throw new IllegalArgumentException();
            cards.add(CardCatalog.create(n(row.get(0)), type));
        }
        return cards;
    }

    static Game decode(Map<String, Object> data) {
        if (n(data.get("format")) != 1) throw new IllegalArgumentException();
        var settings = (List<?>) data.get("settings");
        Game g = new Game(GameSettings.parse((String) settings.get(0), n(settings.get(1)), (String) settings.get(2)));
        var geo = (List<?>) data.get("geometry");
        int w = n(geo.get(0)), h = n(geo.get(1)), left = n(geo.get(2)), bottom = n(geo.get(3));
        if (w < 8 || h < 8 || w > 128 || h > 128 || left < 0 || bottom < 0 || left + 8 > w || bottom + 8 > h)
            throw new IllegalArgumentException();
        var board = new Board();
        board.expand(left, w - 8 - left, bottom, h - 8 - bottom);
        board.clear();
        for (Object o : (List<?>) data.get("board")) {
            var row = (List<?>) o;
            int square = n(row.get(0));
            if (board.get(square) != null) throw new IllegalArgumentException();
            board.set(square, readPiece((List<?>) row.get(1)));
        }
        var captured = new ArrayList<Piece>();
        for (Object p : (List<?>) data.get("captured")) captured.add(readPiece((List<?>) p));
        g.restorePosition(board, captured);
        var cards = (List<?>) data.get("cards");
        var piles = g.cardPiles();
        piles.hand(true).addAll(readCards((List<?>) cards.get(0)));
        piles.hand(false).addAll(readCards((List<?>) cards.get(1)));
        piles.deck().addAll(readCards((List<?>) cards.get(2)));
        piles.discard().addAll(readCards((List<?>) cards.get(3)));
        var history = new ArrayList<String>();
        for (Object line : (List<?>) data.get("history")) history.add((String) line);
        g.setHistory(history);
        ((Map<?, ?>) data.get("repetitions")).forEach((key, count) -> g.repetitions().put((String) key, n(count)));
        var counters = (List<?>) data.get("counters");
        g.setPly(n(counters.get(0)));
        g.setNextPieceId(n(counters.get(1)));
        g.setEnPassant(n(counters.get(2)));
        g.setHalfMoves(n(counters.get(3)));
        g.setScouting(n(counters.get(4)));
        var flags = (List<?>) data.get("flags");
        g.setWhiteTurn((Boolean) flags.get(0));
        var castling = g.castling();
        castling.set(true, true, (Boolean) flags.get(1));
        castling.set(true, false, (Boolean) flags.get(2));
        castling.set(false, true, (Boolean) flags.get(3));
        castling.set(false, false, (Boolean) flags.get(4));
        g.setResult(GameResult.fromId((String) data.get("result")));
        if (data.get("handLimits") instanceof List<?> limits) {
            piles.setLimit(true, n(limits.get(0)));
            piles.setLimit(false, n(limits.get(1)));
        }
        // Загрузка — отдельная ветка партии для памяти соперника (новый gameId из конструктора).
        if (g.ply() < 0 || g.halfMoves() < 0 || g.enPassant() < -1 || g.enPassant() >= board.size()
                || g.scoutingActive() && piles.deck().size() < 2) throw new IllegalArgumentException();
        for (Object o : (List<?>) data.getOrDefault("squareEffects", List.of())) {
            var row = (List<?>) o;
            int square = n(row.get(0));
            board.geometry().rank(square);
            var effect = new SquareEffect(SquareEffect.Type.valueOf((String) row.get(1)),
                    (Boolean) row.get(2), n(row.get(3)), n(row.get(4)));
            if (effect.expiresAt() <= g.ply() || effect.freezeTurns() < 0 || effect.freezeTurns() > 100
                    || effect.blocksEntry() && board.get(square) != null
                    || g.effects().at(square) != null) throw new IllegalArgumentException();
            g.effects().place(square, effect);
        }
        if (data.containsKey("lastMove")) g.setLastMove(decodeUndo((Map<?, ?>) data.get("lastMove"), g));
        return g;
    }

    static void write(Game g, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "save-", ".tmp");
        try {
            Files.writeString(temp, JsonCodec.toJson(encode(g)));
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static Game read(Path file) throws IOException {
        if (!Files.exists(file)) throw new IllegalArgumentException("Сохранённой партии пока нет.");
        if (Files.size(file) > 10_000_000) throw new IllegalArgumentException("Файл сохранения слишком большой.");
        try {
            return decode(JsonCodec.readObject(Files.readString(file)));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Файл сохранения повреждён или несовместим.");
        }
    }
}
