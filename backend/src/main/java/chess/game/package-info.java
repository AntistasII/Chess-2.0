/**
 * Игровое ядро: партия, правила хода и сессия.
 *
 * <h2>Состояние партии</h2>
 * {@link chess.game.Game} — единственная точка входа к позиции. Её состояние закрыто
 * и разложено по частям, каждая из которых следит за своими правилами:
 * <ul>
 *   <li>{@link chess.board.Board} — фигуры и геометрия (расширяется картами);</li>
 *   <li>{@link chess.game.CardPiles} — руки, колода, сброс;</li>
 *   <li>{@link chess.game.SquareEffects} — капканы и белые дыры;</li>
 *   <li>{@link chess.game.CastlingRights} — сохранённые права рокировки;</li>
 *   <li>{@link chess.game.GameSettings} — режим ({@link chess.game.GameMode}), лимит руки,
 *       сложность ({@link chess.game.Difficulty});</li>
 *   <li>очередь, счётчики, итог ({@link chess.game.GameResult}) и версия — поля самой партии.</li>
 * </ul>
 *
 * <h2>Правила</h2>
 * Классы правил не хранят состояния и создаются на одну операцию:
 * {@link chess.game.TurnRules} собирает допустимые действия и исполняет выбранное,
 * {@link chess.game.MoveRules} — ходы и атаки фигур, {@link chess.game.CardRules} —
 * расход карты и вызов её эффекта, {@link chess.game.GameOutcome} — мат, пат и ничьи.
 * Действие описывает {@link chess.game.Action} с видом {@link chess.game.ActionKind};
 * его строковый id — протокол с интерфейсом.
 *
 * <h2>Сессия и внешние форматы</h2>
 * {@link chess.game.GameSession} хранит текущую партию, проверяет версию и очередь;
 * ход автоматического соперника выполняет {@link chess.game.OpponentTurn}, файл
 * сохранения — {@link chess.game.SaveFile}. {@link chess.game.GameView} строит JSON для
 * интерфейса, {@link chess.game.GameSave} — полный снимок; оба формата неизменны между
 * версиями, а строковые значения перечислений берутся из их {@code id()}.
 *
 * <p>Цвет стороны во всём движке — {@code boolean white}: так же, как в фигурах и ИИ.</p>
 */
package chess.game;
