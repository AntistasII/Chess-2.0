package chess.game;

import chess.board.Board;
import chess.board.Geometry;
import chess.cards.Card;
import chess.cards.CardCatalog;
import chess.cards.CardType;
import chess.pieces.Piece;
import chess.pieces.PieceFactory;
import chess.pieces.PieceType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Партия: позиция, карты, очередь хода и итог. Все действия проходят единую проверку
 * безопасности короля.
 *
 * <p>Публичные методы — интерфейс для ИИ, соперников и сессии: только чтение позиции,
 * расчёт на копиях ({@link #simulate}) и команды игрока ({@link #play}, {@link #resign}…).
 * Правила хода ({@link TurnRules}, {@link MoveRules}, {@link CardRules}), итог
 * ({@link GameOutcome}), сохранение и представление работают с состоянием через
 * методы видимости пакета.</p>
 */
public final class Game {
    private GameSettings settings;
    // Доска хранит фигуры и геометрию; размеры могут увеличиваться во время партии.
    private Board board = new Board();
    private final SquareEffects effects;
    private final CardPiles cards;
    private final CastlingRights castling;
    private List<Piece> captured = new ArrayList<>();
    private List<String> history = new ArrayList<>();
    private final Map<String, Integer> repetitions;

    // ply считает действия обеих сторон; halfMoves — действия без хода пешкой и взятия.
    private boolean whiteTurn = true;
    private int ply, halfMoves, enPassant = -1, nextPieceId = 1;
    /** Идентификатор начатой разведки или -1. */
    private int scouting = -1;
    /** Снимок для карты «Отмена хода»: только последний ход фигурой. */
    private MoveUndo lastMove;
    private GameResult result = GameResult.IN_PROGRESS;
    /** Защищает от устаревших команд клиента. */
    private long version;
    // Копии поиска наследуют идентификатор: SecureRandom не вызывается на каждый узел.
    private String gameId;
    // Копия поиска: снимок отмены нужен, только если у следующего игрока есть «Отмена хода».
    private boolean simulation;

    /**
     * Новая партия: начальная расстановка и общая перемешанная колода.
     *
     * @param backRank порядок фигур первой горизонтали в кодах P/N/B/R/Q/K, например RNBQKBNR
     */
    public Game(GameSettings settings, String backRank) {
        this(settings);
        initializeBoard(backRank);
        cards.deck().addAll(CardCatalog.newDeck());
        repetitions.put(positionKey(), 1);
    }

    /**
     * Пустая партия для загрузки: доска и карты заполняются из сохранения.
     */
    Game(GameSettings settings) {
        this.settings = settings;
        this.effects = new SquareEffects();
        this.cards = new CardPiles(settings.handLimit());
        this.castling = new CastlingRights();
        this.repetitions = new HashMap<>();
        this.gameId = UUID.randomUUID().toString();
    }

    private void initializeBoard(String backRank) {
        var geometry = board.geometry();
        for (int file = 0; file < 8; file++) {
            PieceType officer = PieceType.fromCode(backRank.charAt(file));
            board.set(geometry.originalSquare(0, file), newPiece(officer, true));
            board.set(geometry.originalSquare(1, file), newPiece(PieceType.PAWN, true));
            board.set(geometry.originalSquare(6, file), newPiece(PieceType.PAWN, false));
            board.set(geometry.originalSquare(7, file), newPiece(officer, false));
        }
    }

    /**
     * Копирует позицию для поиска; история и счётчик повторений поиску не нужны.
     */
    private Game(Game source) {
        settings = source.settings;
        board = source.board.copy();
        effects = source.effects.copy();
        cards = source.cards.copy();
        castling = source.castling.copy();
        for (var piece : source.captured) captured.add(piece.copy());
        repetitions = new HashMap<>();
        lastMove = source.lastMove; // Снимок неизменяемый; restore всегда копирует его содержимое.
        whiteTurn = source.whiteTurn;
        ply = source.ply;
        halfMoves = source.halfMoves;
        enPassant = source.enPassant;
        nextPieceId = source.nextPieceId;
        scouting = source.scouting;
        result = source.result;
        version = source.version;
        gameId = source.gameId;
        simulation = source.simulation;
    }

    Game copy() {
        return new Game(this);
    }

    // ---------------------------------------------------------------- публичное чтение

    public GameSettings settings() { return settings; }

    public GameMode mode() { return settings.mode(); }

    /** Настройка глубины и времени поиска локального соперника. */
    public Difficulty difficulty() { return settings.difficulty(); }

    /** Текущая вместимость руки игрока (начальная — {@code settings().handLimit()}). */
    public int handLimit(boolean white) { return cards.limit(white); }

    /** Сторона текущего игрока. */
    public boolean whiteTurn() { return whiteTurn; }

    public GameResult result() { return result; }

    public long version() { return version; }

    public String gameId() { return gameId; }

    /** Независимое состояние фигуры для чтения вне игрового пакета. */
    public Piece pieceAt(int square) {
        Piece piece = board.get(square);
        return piece == null ? null : piece.copy();
    }

    public Geometry boardGeometry() { return board.geometry(); }

    /** Неизменяемый список карт; используются только карты доступной поисковой позиции. */
    public List<Card> cards(boolean white) { return List.copyOf(cards.hand(white)); }

    /** Взятые фигуры для оценки возможности возрождения, без доступа к исходным экземплярам. */
    public List<Piece> capturedPieces() { return captured.stream().map(Piece::copy).toList(); }

    /** Поля заклинаний по возрастанию клетки. */
    public Map<Integer, SquareEffect> squareEffects() { return effects.sorted(); }

    /** Полная публичная история; скрытые карты в неё не записываются. */
    public List<String> moveHistory() { return List.copyOf(history); }

    /** Отсутствующий король также считается небезопасной позицией. */
    public boolean inCheck(boolean white) {
        for (int s = 0; s < board.size(); s++) {
            Piece p = board.get(s);
            if (p != null && p.white() == white && p.type() == PieceType.KING) return attacked(s, !white);
        }
        return true;
    }

    /** Сохранённое право, даже если путь временно занят или атакован. */
    public boolean hasCastlingRight(boolean white, boolean shortSide) {
        Piece king = board.get(kingStartSquare(white));
        Piece rook = board.get(rookStartSquare(white, shortSide));
        return castling.allowed(white, shortSide)
                && king != null && king.type() == PieceType.KING && king.white() == white && !king.moved()
                && rook != null && rook.type() == PieceType.ROOK && rook.white() == white && !rook.moved();
    }

    /** Проверка атак для оценки позиции: заморозка сохраняет атаки, астрал исключает их. */
    public boolean isSquareAttacked(int target, boolean byWhite) { return attacked(target, byWhite); }

    /** Геометрические варианты фигуры для оценки подвижности; не список законных команд. */
    public List<Action> movementOptions(int square) { return List.copyOf(new MoveRules(this).movesFrom(square)); }

    // ---------------------------------------------------------------- действия и расчёт

    /** Исполняет кандидатов на копии и отбрасывает те, после которых свой король под шахом. */
    public List<Action> legalActions() { return new TurnRules(this).legalActions(); }

    /** Законные обратные взятия; фильтрация до симуляции не перебирает ненужные карты. */
    public List<Action> legalRecaptures(int square) {
        return new TurnRules(this).legalActions(a -> a.isMove() && a.to() == square);
    }

    /** Варианты восстановления материала: обратное взятие, возрождение, обращение или отмена. */
    public List<Action> materialRecoveryActions(int square) {
        return new TurnRules(this).legalActions(a -> {
            if (a.isMove()) return a.to() == square;
            if (!a.isCard()) return false;
            // Во время разведки действия относятся к уже сыгранной карте, её нет в руке.
            return cards.hand(whiteTurn).stream().anyMatch(c -> c.id() == a.cardId() && switch (c.type()) {
                case REVIVE_PAWN, REVIVE_PIECE, CONVERT, UNDO_MOVE -> true;
                default -> false;
            });
        });
    }

    /** Допустимое действие вместе с уже рассчитанной позицией после него. */
    public record Transition(Action action, Game next) {}

    /**
     * То же, что legalActions, но возвращает и проверенные позиции: поиску не нужно
     * повторно копировать партию для каждого хода.
     */
    public List<Transition> legalTransitions() { return new TurnRules(this).legalTransitions(action -> true); }

    /** Только допустимые действия, прошедшие фильтр; фильтр проверяется до расчёта позиции. */
    public List<Transition> legalTransitions(Predicate<Action> filter) { return new TurnRules(this).legalTransitions(filter); }

    /**
     * Рассчитывает позицию после действия на независимой копии без истории и смены версии.
     * Действие берётся из legalActions; этот метод не применяет команду к живой партии.
     */
    public Game simulate(Action action) {
        Game next = copy();
        next.simulation = true;
        next.applyUnchecked(action);
        return next;
    }

    /**
     * Копия для поиска текущего игрока: карты соперника, колода и невидимые капканы
     * соперника заменены неизвестными. Изменяемое состояние исходной партии недоступно.
     */
    public Game observationForCurrentPlayer() {
        Game observation = copy();
        observation.history = new ArrayList<>(history);
        observation.effects.hideFrom(whiteTurn);
        observation.cards.hideFrom(whiteTurn);
        return observation;
    }

    /** Снимок только доступной сопернику информации, с его рукой и допустимыми действиями. */
    public Map<String, Object> opponentState() { return GameView.of(this, whiteTurn, true); }

    // ---------------------------------------------------------------- команды игрока

    /** Принимает только действие из актуального списка и обновляет историю, версию и итог. */
    public void play(String id) {
        Action action = legalActions().stream().filter(x -> x.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Действие недоступно. Выберите допустимый ход или карту."));
        String label = (whiteTurn ? "Белые: " : "Чёрные: ") + ActionDescription.describe(this, action);
        applyUnchecked(action);
        version++;
        history.add(label);
        repetitions.merge(positionKey(), 1, Integer::sum);
        refreshResult();
    }

    /** Расходует разведку до показа карт: после раскрытия отменить её нельзя. */
    public void startScout(int cardId) { new TurnRules(this).startScout(cardId); }

    /** Фиксирует сдачу игрока, не перемещая фигуры и не расходуя ход. */
    public void resign(boolean white) {
        if (result.finished()) throw new IllegalArgumentException("Партия уже завершена");
        result = GameResult.resignation(white);
        version++;
    }

    /** Фиксирует ничью по заявлению текущего игрока. */
    public void claimDraw() {
        if (!canClaimDraw()) throw new IllegalArgumentException("Ничья пока недоступна");
        result = GameResult.CLAIMED_DRAW;
        version++;
    }

    // ---------------------------------------------------------------- состояние для правил пакета

    Board board() { return board; }

    SquareEffects effects() { return effects; }

    CardPiles cardPiles() { return cards; }

    /** Изменяемая рука игрока (для правил, загрузки и подготовки позиций в тестах). */
    List<Card> hand(boolean white) { return cards.hand(white); }

    CastlingRights castling() { return castling; }

    /** Изменяемый список взятых фигур (для правил и загрузки). */
    List<Piece> captured() { return captured; }

    /** Изменяемая история (для загрузки и сессии). */
    List<String> history() { return history; }

    Map<String, Integer> repetitions() { return repetitions; }

    int ply() { return ply; }

    int halfMoves() { return halfMoves; }

    int enPassant() { return enPassant; }

    int nextPieceId() { return nextPieceId; }

    int scouting() { return scouting; }

    boolean scoutingActive() { return scouting >= 0; }

    MoveUndo lastMove() { return lastMove; }

    boolean simulation() { return simulation; }

    void setWhiteTurn(boolean white) { whiteTurn = white; }

    void setPly(int value) { ply = value; }

    void setHalfMoves(int value) { halfMoves = value; }

    void setEnPassant(int square) { enPassant = square; }

    void setNextPieceId(int value) { nextPieceId = value; }

    void setScouting(int cardId) { scouting = cardId; }

    void setLastMove(MoveUndo undo) { lastMove = undo; }

    void setResult(GameResult value) { result = value; }

    void setVersion(long value) { version = value; }

    void setGameId(String value) { gameId = value; }

    void setHistory(List<String> lines) { history = new ArrayList<>(lines); }

    /** Отмена хода возвращает доску и взятые фигуры из снимка. */
    void restorePosition(Board restoredBoard, List<Piece> restoredCaptured) {
        board = restoredBoard;
        captured = new ArrayList<>(restoredCaptured);
    }

    /** Новая фигура текущей партии с уникальным идентификатором. */
    Piece newPiece(PieceType type, boolean white) {
        return PieceFactory.create(nextPieceId++, type, white);
    }

    /** Завершает полуход: передаёт очередь и снимает истёкшие эффекты. */
    void advanceTurn() {
        ply++;
        whiteTurn = !whiteTurn;
        effects.expire(ply);
        for (Piece piece : board)
            if (piece != null) piece.expireEffects(ply);
    }

    boolean blocked(int square) { return effects.blocksEntry(square); }

    boolean attacked(int target, boolean byWhite) { return new MoveRules(this).attacked(target, byWhite); }

    /** Координаты вычисляются по текущей ширине, а не по фиксированному числу 8. */
    String square(int square) { return board.geometry().square(square); }

    /** Стартовые линии остаются привязаны к исходной расстановке после расширения. */
    int pawnStartRank(boolean white) { return board.geometry().originRank() + (white ? 1 : 6); }

    int kingStartSquare(boolean white) { return board.geometry().originalSquare(white ? 0 : 7, 4); }

    int rookStartSquare(boolean white, boolean shortSide) {
        return board.geometry().originalSquare(white ? 0 : 7, shortSide ? 7 : 0);
    }

    /** Стартовые клетки для возрождения фигуры соответствующего типа и стороны. */
    List<Integer> startingSquares(PieceType type, boolean white) {
        var geometry = board.geometry();
        int base = geometry.originalSquare(white ? 0 : 7, 0);
        return switch (type) {
            case PAWN -> {
                var squares = new ArrayList<Integer>();
                for (int f = 0; f < 8; f++) squares.add(geometry.originalSquare(white ? 1 : 6, f));
                yield squares;
            }
            case ROOK -> List.of(base, base + 7);
            case KNIGHT -> List.of(base + 1, base + 6);
            case BISHOP -> List.of(base + 2, base + 5);
            case QUEEN -> List.of(base + 3);
            case KING -> List.of();
        };
    }

    /**
     * Добавляет пустые линии, переносит право взятия на проходе и поля заклинаний и
     * сбрасывает счётчик повторений прежней геометрии. Завершение действия, история и
     * версия остаются ответственностью {@link #play}.
     */
    void expandBoard(int left, int right, int bottom, int top) {
        Board.Expansion expansion = board.expand(left, right, bottom, top);
        boolean changed = !expansion.before().equals(expansion.after());
        if (changed) lastMove = null;
        enPassant = expansion.remap(enPassant);
        effects.remap(expansion);
        if (changed) repetitions.clear();
    }

    /** Сохраняет взятую фигуру без эффектов для возможного возрождения. */
    void capture(Piece piece, int square) {
        disableCastle(piece, square);
        captured.add(PieceFactory.create(piece.id(), piece.type(), piece.white()));
    }

    /** Движение или взятие короля и стартовой ладьи необратимо убирает право на рокировку. */
    void disableCastle(Piece piece, int square) {
        if (piece.type() == PieceType.KING) castling.revokeAll(piece.white());
        if (piece.type() == PieceType.ROOK)
            for (boolean shortSide : new boolean[]{true, false})
                if (square == rookStartSquare(piece.white(), shortSide)) castling.set(piece.white(), shortSide, false);
    }

    /** Подмена настроек для подготовки позиций в тестах и стенде сравнения ИИ. */
    void replaceSettings(GameSettings value) {
        settings = value;
        cards.setLimit(true, value.handLimit());
        cards.setLimit(false, value.handLimit());
    }

    /** Определяет мат с учётом спасения картами, пат и автоматические ничьи. */
    void refreshResult() { new GameOutcome(this).refreshResult(); }

    /** Исполняет уже выбранное действие и завершает полуход; используется и на копиях для поиска. */
    void applyUnchecked(Action action) { new TurnRules(this).applyUnchecked(action); }

    /** Проверяет право заявить ничью; незавершённая разведка исключает заявление. */
    boolean canClaimDraw() { return new GameOutcome(this).canClaimDraw(); }

    /** Для повторений учитывает не только доску, но и права, эффекты, карты и взятые фигуры. */
    String positionKey() { return new GameOutcome(this).positionKey(); }

    /** Карта текущего игрока с этим идентификатором. */
    Card cardInHand(int cardId) { return cards.find(whiteTurn, cardId); }

    boolean holds(boolean white, CardType type) { return cards.holds(white, type); }

    /** Вместимость рук отличается от начальной (после «Инфляции» или «Шринкфляции»). */
    boolean handLimitsChanged() {
        return cards.limit(true) != settings.handLimit() || cards.limit(false) != settings.handLimit();
    }
}
