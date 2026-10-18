package chess.game;

import chess.board.Board;
import chess.board.Geometry;

import chess.cards.Card;
import chess.cards.CardCatalog;
import chess.cards.CardType;
import chess.pieces.Piece;
import chess.pieces.PieceFactory;

import java.util.*;

/**
 * Состояние партии и общий цикл хода. Все действия проходят единую проверку безопасности короля.
 */
public final class Game {
    // Доска хранит фигуры и геометрию; размеры могут увеличиваться во время партии.
    Board board = new Board();
    Map<Integer, SquareEffect> squareEffects = new HashMap<>();
    MoveUndo lastMove;
    List<Card> whiteHand = new ArrayList<>(), blackHand = new ArrayList<>(), deck = new ArrayList<>(), discard = new ArrayList<>();
    List<Piece> captured = new ArrayList<>();
    List<String> history = new ArrayList<>();
    String gameId = UUID.randomUUID().toString();
    Map<String, Integer> repetitions = new HashMap<>();
    // ply считает действия обеих сторон; version защищает от устаревших команд клиента.
    boolean whiteTurn = true;
    int ply, nextPieceId = 1, enPassant = -1, halfMoves, handLimit;
    boolean wKingCastle = true, wQueenCastle = true, bKingCastle = true, bQueenCastle = true;
    String mode, difficulty, result = "";
    long version;
    int scouting = -1;

    /**
     * Проверяет настройки и создаёт исходную доску с общей перемешанной колодой.
     */
    public Game(String mode, int handLimit, String difficulty, String backRank) {
        this(mode, handLimit, difficulty);
        initializeBoard(backRank);
        deck = CardCatalog.newDeck();
        repetitions.put(positionKey(), 1);
    }

    /**
     * Пустая партия для загрузки: доска и карты заполняются из сохранения.
     */
    Game(String mode, int handLimit, String difficulty) {
        if (!Set.of("ai", "human", "chatgpt", "network").contains(mode))
            throw new IllegalArgumentException("Неизвестный режим");
        if (handLimit < 1 || handLimit > 12) throw new IllegalArgumentException("Лимит карт: от 1 до 12");
        if (!Set.of("easy", "normal", "hard").contains(difficulty))
            throw new IllegalArgumentException("Неизвестная сложность");
        this.mode = mode;
        this.handLimit = handLimit;
        this.difficulty = difficulty;
    }

    private void initializeBoard(String backRank) {
        for (int file = 0; file < 8; file++) {
            board.set(board.geometry().originalSquare(0, file), PieceFactory.create(nextPieceId++, backRank.charAt(file), true));
            board.set(board.geometry().originalSquare(1, file), PieceFactory.create(nextPieceId++, 'P', true));
            board.set(board.geometry().originalSquare(6, file), PieceFactory.create(nextPieceId++, 'P', false));
            board.set(board.geometry().originalSquare(7, file), PieceFactory.create(nextPieceId++, backRank.charAt(file), false));
        }
    }

    /**
     * Копирует позицию для поиска; история и счётчик повторений поиску не нужны.
     */
    private Game(Game source) {
        board = source.board.copy();
        squareEffects = new HashMap<>(source.squareEffects);
        lastMove = source.lastMove; // Снимок неизменяемый; restore всегда копирует его содержимое.
        gameId = source.gameId;
        // Карты неизменяемы: достаточно копировать списки, сами экземпляры можно разделять.
        whiteHand = new ArrayList<>(source.whiteHand);
        blackHand = new ArrayList<>(source.blackHand);
        deck = new ArrayList<>(source.deck);
        discard = new ArrayList<>(source.discard);
        for (var piece : source.captured) captured.add(piece.copy());
        whiteTurn = source.whiteTurn;
        ply = source.ply;
        nextPieceId = source.nextPieceId;
        enPassant = source.enPassant;
        halfMoves = source.halfMoves;
        handLimit = source.handLimit;
        wKingCastle = source.wKingCastle;
        wQueenCastle = source.wQueenCastle;
        bKingCastle = source.bKingCastle;
        bQueenCastle = source.bQueenCastle;
        mode = source.mode;
        difficulty = source.difficulty;
        result = source.result;
        version = source.version;
        scouting = source.scouting;
    }

    Game copy() {
        return new Game(this);
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
        Piece piece = board.get(square);
        return piece == null ? null : piece.copy();
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
        observation.squareEffects.entrySet().removeIf(e -> !e.getValue().visibleTo(whiteTurn));
        maskCards(observation.hand(!whiteTurn), -100);
        maskCards(observation.deck, -200);
        maskCards(observation.discard, -300);
        return observation;
    }

    /**
     * Полная публичная история для соперника; скрытые карты в неё не записываются.
     */
    public List<String> moveHistory() {
        return List.copyOf(history);
    }

    public String gameId() {
        return gameId;
    }

    /**
     * Снимок только доступной сопернику информации, с его рукой и допустимыми действиями.
     */
    public Map<String, Object> opponentState() {
        return GameView.of(this, whiteTurn, true);
    }

    /**
     * Заменяет содержимое списка неизвестными картами, сохраняя его размер.
     */
    private static void maskCards(List<Card> cards, int firstId) {
        for (int i = 0; i < cards.size(); i++)
            cards.set(i, CardCatalog.create(firstId - i, CardType.UNKNOWN));
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
            if (board.get(s) != null && board.get(s).white() == white && board.get(s).type() == 'K')
                return attacked(s, !white);
        return true;
    }

    /**
     * Геометрия текущей доски без доступа к её изменяемым клеткам.
     */
    public Map<Integer, SquareEffect> squareEffects() { return Map.copyOf(squareEffects); }

    boolean blocked(int square) {
        SquareEffect effect = squareEffects.get(square);
        return effect != null && effect.blocksEntry();
    }

    /** Сохранённое право, даже если путь временно занят или атакован. */
    public boolean hasCastlingRight(boolean white, boolean shortSide) {
        boolean flag = white ? (shortSide ? wKingCastle : wQueenCastle) : (shortSide ? bKingCastle : bQueenCastle);
        Piece king = board.get(kingStartSquare(white));
        Piece rook = board.get(rookStartSquare(white, shortSide));
        return flag && king != null && king.type() == 'K' && king.white() == white && !king.moved()
                && rook != null && rook.type() == 'R' && rook.white() == white && !rook.moved();
    }

    public Geometry boardGeometry() {
        return board.geometry();
    }

    /**
     * Координаты вычисляются по текущей ширине, а не по фиксированному числу 8.
     */
    String square(int square) {
        return board.geometry().square(square);
    }

    /**
     * Стартовые линии остаются привязаны к исходной расстановке после расширения.
     */
    int pawnStartRank(boolean white) {
        return board.geometry().originRank() + (white ? 1 : 6);
    }

    int kingStartSquare(boolean white) {
        return board.geometry().originalSquare(white ? 0 : 7, 4);
    }

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
        if (!expansion.before().equals(expansion.after())) lastMove = null;
        enPassant = expansion.remap(enPassant);
        var remapped = new HashMap<Integer, SquareEffect>();
        squareEffects.forEach((square, effect) -> remapped.put(expansion.remap(square), effect));
        squareEffects = remapped;
        if (!expansion.before().equals(expansion.after())) repetitions.clear();
    }

    /**
     * Проверка атак для оценки позиции: заморозка сохраняет атаки, астрал исключает их.
     */
    public boolean isSquareAttacked(int target, boolean byWhite) {
        return attacked(target, byWhite);
    }

    boolean attacked(int target, boolean byWhite) {
        return new MoveRules(this).attacked(target, byWhite);
    }

    /** Законные обратные взятия; фильтрация до симуляции не перебирает ненужные карты. */
    public List<Action> legalRecaptures(int square) {
        return new TurnRules(this).legalActions(a -> a.kind().equals("move") && a.to() == square);
    }

    /** Варианты восстановления материала: обратное взятие, возрождение, обращение или отмена. */
    public List<Action> materialRecoveryActions(int square) {
        return new TurnRules(this).legalActions(a -> {
            if (a.kind().equals("move")) return a.to() == square;
            if (!a.kind().equals("card")) return false;
            return hand(whiteTurn).stream().anyMatch(c -> c.id() == a.cardId() && switch (c.type()) {
                case REVIVE_PAWN, REVIVE_PIECE, CONVERT, UNDO_MOVE -> true;
                default -> false;
            });
        });
    }

    /** Геометрические варианты фигуры для оценки подвижности; не список законных команд. */
    public List<Action> movementOptions(int square) {
        return List.copyOf(new MoveRules(this).movesFrom(square));
    }

    /** Исполняет кандидатов на копии и отбрасывает те, после которых свой король под шахом. */
    public List<Action> legalActions() {
        return new TurnRules(this).legalActions();
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
        String label = (whiteTurn ? "Белые: " : "Чёрные: ") + ActionDescription.describe(this, a);
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
        new TurnRules(this).startScout(cardId);
    }

    /**
     * Исполняет уже выбранное действие и завершает полуход; используется также на копиях для поиска.
     */
    void applyUnchecked(Action action) {
        new TurnRules(this).applyUnchecked(action);
    }

    /**
     * Дополняет верхние карты для разведки; существующая верхняя карта сохраняет своё место.
     */
    void ensureScoutDeck() {
        new TurnRules(this).ensureScoutDeck();
    }

    /**
     * Сохраняет взятую фигуру без эффектов для возможного возрождения.
     */
    void capture(Piece p, int s) {
        disableCastle(p, s);
        Piece gone = PieceFactory.create(p.id(), p.type(), p.white());
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
        new GameOutcome(this).refreshResult();
    }

    /**
     * Проверяет право заявить ничью; незавершённая разведка исключает заявление.
     */
    boolean canClaimDraw() {
        return new GameOutcome(this).canClaimDraw();
    }

    /**
     * Фиксирует сдачу игрока, не перемещая фигуры и не расходуя ход.
     */
    public void resign(boolean white) {
        if (!result.isEmpty()) throw new IllegalArgumentException("Партия уже завершена");
        result = white ? "whiteResigned" : "blackResigned";
        version++;
    }

    /** Фиксирует ничью по заявлению текущего игрока. */
    public void claimDraw() {
        if (!canClaimDraw()) throw new IllegalArgumentException("Ничья пока недоступна");
        result = "claimedDraw";
        version++;
    }

    /**
     * Для повторений учитывает не только доску, но и права, эффекты, карты и взятые фигуры.
     */
    private String positionKey() {
        return new GameOutcome(this).positionKey();
    }

}
