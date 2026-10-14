package chess.game;

import chess.board.Board;

import chess.cards.Card;
import chess.cards.CardCatalog;
import chess.cards.CardType;
import chess.pieces.Piece;


import java.util.*;

/**
 * Состояние партии и общий цикл хода. Все действия проходят единую проверку безопасности короля.
 */
public final class Game {
    // Доска хранит фигуры и геометрию; размеры могут увеличиваться во время партии.
    Board board = new Board();
    List<Card> whiteHand = new ArrayList<>(), blackHand = new ArrayList<>(), deck = new ArrayList<>(), discard = new ArrayList<>();
    List<Piece> captured = new ArrayList<>();
    List<String> history = new ArrayList<>();
    String gameId = UUID.randomUUID().toString();
    Map<String, Integer> repetitions = new HashMap<>();
    // ply считает действия обеих сторон; version защищает от устаревших команд клиента.
    boolean whiteTurn = true;
    int ply, nextPieceId = 1, enPassant = -1, halfMoves, handLimit = 3;
    boolean wKingCastle = true, wQueenCastle = true, bKingCastle = true, bQueenCastle = true;
    String mode = "ai", difficulty = "normal", result = "";
    long version;
    int scouting = -1;

    /**
     * Проверяет настройки и создаёт исходную доску с общей перемешанной колодой.
     */
    public Game(String mode, int handLimit, String difficulty) {
        if (!Set.of("ai", "human", "chatgpt", "network").contains(mode)) throw new IllegalArgumentException("Неизвестный режим");
        if (handLimit < 1 || handLimit > 12) throw new IllegalArgumentException("Лимит карт: от 1 до 12");
        if (!Set.of("easy", "normal", "hard").contains(difficulty))
            throw new IllegalArgumentException("Неизвестная сложность");
        this.mode = mode;
        this.handLimit = handLimit;
        this.difficulty = difficulty;
        String line = "RNBQKBNR";
        for (int file = 0; file < 8; file++) {
            board.set(board.geometry().originalSquare(0, file), new Piece(nextPieceId++, line.charAt(file), true));
            board.set(board.geometry().originalSquare(1, file), new Piece(nextPieceId++, 'P', true));
            board.set(board.geometry().originalSquare(6, file), new Piece(nextPieceId++, 'P', false));
            board.set(board.geometry().originalSquare(7, file), new Piece(nextPieceId++, line.charAt(file), false));
        }
        deck = CardCatalog.newDeck();
        repetitions.put(positionKey(), 1);
    }

    private Game() {
    }

    /**
     * Копирует состояние для расчётов; история и счётчик повторений поиску не нужны.
     */
    Game copy() {
        var g = new Game();
        g.board = board.copy();
        g.gameId = gameId;
        // Карты неизменяемы: достаточно копировать списки, сами экземпляры можно разделять.
        g.whiteHand = new ArrayList<>(whiteHand);
        g.blackHand = new ArrayList<>(blackHand);
        g.deck = new ArrayList<>(deck);
        g.discard = new ArrayList<>(discard);
        for (var p : captured) g.captured.add(p.copy());
        g.whiteTurn = whiteTurn;
        g.ply = ply;
        g.nextPieceId = nextPieceId;
        g.enPassant = enPassant;
        g.halfMoves = halfMoves;
        g.handLimit = handLimit;
        g.wKingCastle = wKingCastle;
        g.wQueenCastle = wQueenCastle;
        g.bKingCastle = bKingCastle;
        g.bQueenCastle = bQueenCastle;
        g.mode = mode;
        g.difficulty = difficulty;
        g.result = result;
        g.version = version;
        g.scouting = scouting;
        return g;
    }

    /**
     * Сторона текущего игрока.
     */
    public boolean whiteTurn() {
        return whiteTurn;
    }

    /**
     * Настройка глубины и времени поиска локального соперника.
     */
    public String difficulty() {
        return difficulty;
    }

    /**
     * Независимое состояние фигуры для чтения вне игрового пакета.
     */
    public Piece pieceAt(int square) {
        return board.get(square) == null ? null : board.get(square).copy();
    }

    /**
     * Неизменяемый список карт; используются только карты доступной поисковой позиции.
     */
    public List<Card> cards(boolean white) {
        return List.copyOf(hand(white));
    }

    /**
     * Взятые фигуры для оценки возможности возрождения, без доступа к исходным экземплярам.
     */
    public List<Piece> capturedPieces() {
        return captured.stream().map(Piece::copy).toList();
    }

    /**
     * Создаёт копию для поиска текущего игрока и скрывает реальные карты соперника и колоды.
     * Изменяемые списки и доска исходной партии остаются внутри игрового пакета.
     */
    public Game observationForCurrentPlayer() {
        Game observation = copy();
        observation.history = new ArrayList<>(history);
        maskCards(observation.hand(!whiteTurn), -100);
        maskCards(observation.deck, -200);
        maskCards(observation.discard, -300);
        return observation;
    }

    /** Полная публичная история для соперника; скрытые карты в неё не записываются. */
    public List<String> moveHistory() { return List.copyOf(history); }
    public String gameId() { return gameId; }

    /** Снимок только доступной сопернику информации, с его рукой и допустимыми действиями. */
    public Map<String, Object> opponentState() {
        return GameView.of(this, whiteTurn, true);
    }

    /**
     * Заменяет содержимое списка неизвестными картами, сохраняя его размер.
     */
    private static void maskCards(List<Card> cards, int firstId) {
        for (int i = 0; i < cards.size(); i++) cards.set(i, new Card(firstId - i, CardType.UNKNOWN));
    }

    /**
     * Рассчитывает позицию после действия на независимой копии без истории и смены версии.
     * Действие берётся из legalActions; этот метод не применяет команду к живой партии.
     */
    public Game simulate(Action action) {
        Game next = copy();
        next.applyUnchecked(action);
        return next;
    }

    /**
     * Выбирает руку нужной стороны.
     */
    List<Card> hand(boolean white) {
        return white ? whiteHand : blackHand;
    }

    /**
     * Отсутствующий король также считается небезопасной позицией.
     */
    public boolean inCheck(boolean white) {
        for (int s = 0; s < board.size(); s++)
            if (board.get(s) != null && board.get(s).white() == white && board.get(s).type() == 'K') return attacked(s, !white);
        return true;
    }

    /** Геометрия текущей доски без доступа к её изменяемым клеткам. */
    public Board.Geometry boardGeometry() { return board.geometry(); }

    /** Координаты вычисляются по текущей ширине, а не по фиксированному числу 8. */
    String square(int square) { return board.geometry().square(square); }

    /** Стартовые линии остаются привязаны к исходной расстановке после расширения. */
    int pawnStartRank(boolean white) { return board.geometry().originRank() + (white ? 1 : 6); }
    int kingStartSquare(boolean white) { return board.geometry().originalSquare(white ? 0 : 7, 4); }
    int rookStartSquare(boolean white, boolean shortSide) {
        return board.geometry().originalSquare(white ? 0 : 7, shortSide ? 7 : 0);
    }

    /**
     * Внутренняя операция для будущих карт расширения: добавляет пустые линии,
     * переносит право взятия на проходе и сбрасывает счётчик повторений прежней геометрии.
     * Завершение действия, история и версия остаются ответственностью Game.play.
     */
    void expandBoard(int left, int right, int bottom, int top) {
        Board.Expansion expansion = board.expand(left, right, bottom, top);
        enPassant = expansion.remap(enPassant);
        if (!expansion.before().equals(expansion.after())) repetitions.clear();
    }

    boolean attacked(int target, boolean byWhite) {
        return new MoveRules(this).attacked(target, byWhite);
    }

    /**
     * Исполняет кандидатов на копии и отбрасывает те, после которых свой король под шахом.
     */
    public List<Action> legalActions() {
        if (!result.isEmpty()) return List.of();
        var out = new ArrayList<Action>();
        boolean actor = whiteTurn;
        for (Action a : pseudoActions()) {
            Game candidate = copy();
            candidate.applyUnchecked(a);
            if (!candidate.inCheck(actor)) out.add(a);
        }
        return out;
    }

    /**
     * Собирает ходы и карты до проверки шаха; во время разведки разрешён только выбор порядка.
     */
    private List<Action> pseudoActions() {
        if (scouting >= 0) {
            Card c = new Card(scouting, CardType.SCOUT);
            return List.of(Action.card(c, -1, -1, "keep"), Action.card(c, -1, -1, "reverse"));
        }
        var out = new ArrayList<>(new MoveRules(this).moves());
        if (hand(whiteTurn).size() < handLimit && (!deck.isEmpty() || !discard.isEmpty()) && !inCheck(whiteTurn))
            out.add(Action.draw());
        var rules = new CardRules(this);
        for (Card card : hand(whiteTurn)) rules.addActions(out, card);
        return out;
    }

    /**
     * Стартовые клетки для возрождения фигуры соответствующего типа и стороны.
     */
    List<Integer> startingSquares(char type, boolean white) {
        int base = board.geometry().originalSquare(white ? 0 : 7, 0);
        return switch (type) {
            case 'P' -> {
                var squares = new ArrayList<Integer>();
                for (int f = 0; f < 8; f++) squares.add(board.geometry().originalSquare(white ? 1 : 6, f));
                yield squares;
            }
            case 'R' -> List.of(base, base + 7);
            case 'N' -> List.of(base + 1, base + 6);
            case 'B' -> List.of(base + 2, base + 5);
            case 'Q' -> List.of(base + 3);
            default -> List.of();
        };
    }

    /**
     * Принимает только действие из актуального списка и обновляет историю, версию и результат.
     */
    public void play(String id) {
        Action a = legalActions().stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Действие недоступно. Выберите допустимый ход или карту."));
        String label = (whiteTurn ? "Белые: " : "Чёрные: ") + describe(a);
        applyUnchecked(a);
        version++;
        history.add(label);
        repetitions.merge(positionKey(), 1, Integer::sum);
        refreshResult();
    }

    /**
     * Расходует разведку до показа карт: после раскрытия отменить её нельзя.
     */
    public void startScout(int cardId) {
        if (scouting >= 0 || legalActions().stream().noneMatch(a -> a.cardId() == cardId && a.option().equals("keep")))
            throw new IllegalArgumentException("Разведка недоступна");
        Card c = hand(whiteTurn).stream().filter(x -> x.id() == cardId && x.type() == CardType.SCOUT).findFirst().orElseThrow();
        ensureScoutDeck();
        hand(whiteTurn).remove(c);
        discard.add(c);
        scouting = cardId;
        version++;
    }

    /**
     * Формирует читаемую запись действия для истории партии.
     */
    private String describe(Action a) {
        if (a.kind().equals("draw")) return "взяли карту";
        if (a.kind().equals("move")) {
            String piece = switch (board.get(a.from()).type()) {
                case 'P' -> "Пешка";
                case 'N' -> "Конь";
                case 'B' -> "Слон";
                case 'R' -> "Ладья";
                case 'Q' -> "Ферзь";
                case 'K' -> "Король";
                default -> "Фигура";
            };
            return piece + " " + square(a.from()) + " → " + square(a.to())
                    + (a.option().equals("castle") ? " (рокировка)"
                        : a.option().isEmpty() ? "" : " = " + a.option());
        }
        Card card = scouting == a.cardId() ? new Card(scouting, CardType.SCOUT) : hand(whiteTurn).stream().filter(c -> c.id() == a.cardId()).findFirst().orElseThrow();
        return card.name() + (a.to() >= 0 ? " · " + square(a.to()) : "") + (a.other() >= 0 && card.type() == CardType.SWAP ? " ↔ " + square(a.other()) : "");
    }

    /**
     * Исполняет уже выбранное действие и завершает полуход; используется также на копиях для поиска.
     */
    void applyUnchecked(Action a) {
        int oldEp = enPassant;
        enPassant = -1;
        if (scouting >= 0) {
            if (a.option().equals("reverse")) Collections.swap(deck, 0, 1);
            scouting = -1;
        } else if (a.kind().equals("draw")) {
            ensureDeck();
            hand(whiteTurn).add(deck.remove(0));
        } else if (a.kind().equals("move")) {
            new MoveRules(this).apply(a, oldEp);
        } else {
            new CardRules(this).apply(a);
        }
        ply++;
        whiteTurn = !whiteTurn;
        for (Piece p : board) if (p != null) p.expireEffects(ply);
    }

    /**
     * При пустой колоде перемешивает использованные карты, сохраняя общую колоду для двух игроков.
     */
    void ensureDeck() {
        if (deck.isEmpty()) {
            deck.addAll(discard);
            discard.clear();
            Collections.shuffle(deck);
        }
    }

    /**
     * Дополняет верхние карты для разведки; существующая верхняя карта сохраняет своё место.
     */
    void ensureScoutDeck() {
        if (deck.size() < 2 && !discard.isEmpty()) {
            Collections.shuffle(discard);
            deck.addAll(discard);
            discard.clear();
        }
    }

    /**
     * Сохраняет взятую фигуру без эффектов для возможного возрождения.
     */
    void capture(Piece p, int s) {
        disableCastle(p, s);
        Piece gone = new Piece(p.id(), p.type(), p.white());
        captured.add(gone);
    }

    /**
     * Движение или взятие короля и стартовой ладьи необратимо убирает право на рокировку.
     */
    void disableCastle(Piece p, int s) {
        if (p.type() == 'K') {
            if (p.white()) {
                wKingCastle = false;
                wQueenCastle = false;
            } else {
                bKingCastle = false;
                bQueenCastle = false;
            }
        }
        if (p.type() == 'R') {
            if (p.white() && s == rookStartSquare(true, false)) wQueenCastle = false;
            if (p.white() && s == rookStartSquare(true, true)) wKingCastle = false;
            if (!p.white() && s == rookStartSquare(false, false)) bQueenCastle = false;
            if (!p.white() && s == rookStartSquare(false, true)) bKingCastle = false;
        }
    }

    /**
     * Определяет мат с учётом спасения картами, пат и автоматические ничьи.
     */
    void refreshResult() {
        if (legalActions().isEmpty()) result = inCheck(whiteTurn) ? (whiteTurn ? "black" : "white") : "stalemate";
        // Автоматические пороги ничьей; повторение требует совпадения карт и эффектов.
        else if (halfMoves >= 150) result = "seventyFiveMoves";
        else if (repetitions.getOrDefault(positionKey(), 0) >= 5) result = "repetition";
    }

    /**
     * Проверяет право заявить ничью; незавершённая разведка исключает заявление.
     */
    boolean canClaimDraw() {
        return scouting < 0 && result.isEmpty() && (halfMoves >= 100 || repetitions.getOrDefault(positionKey(), 0) >= 3);
    }

    /**
     * Фиксирует заявленную ничью и обновляет версию состояния.
     */
    public void claimDraw() {
        if (!canClaimDraw()) throw new IllegalArgumentException("Ничья пока недоступна");
        result = "claimedDraw";
        version++;
    }

    /**
     * Для повторений учитывает не только доску, но и права, эффекты, карты и взятые фигуры.
     */
    private String positionKey() {
        var s = new StringBuilder().append(board.geometry()).append(':').append(whiteTurn).append(':').append(enPassant).append(':').append(wKingCastle).append(wQueenCastle).append(bKingCastle).append(bQueenCastle);
        for (Piece p : board) {
            s.append('/');
            if (p != null)
                s.append(p.type()).append(p.white()).append(p.moved()).append(p.boost()).append(p.shield()).append(Math.max(0, p.frozenUntil() - ply)).append(':').append(Math.max(0, p.astralUntil() - ply));
        }
        for (var list : List.of(whiteHand, blackHand, deck, discard)) {
            s.append('|');
            for (Card c : list) s.append(c.type()).append(',');
        }
        s.append('|');
        captured.stream().map(p -> "" + p.type() + p.white()).sorted().forEach(s::append);
        return s.toString();
    }

}
