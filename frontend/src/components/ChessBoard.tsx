import { Icon } from './Icon';
import { PieceIcon } from './PieceIcon';
import { pieceNames } from '../game/catalog';
import { boardOrder, fileName, squareName, standardBoard } from '../game/board';
import type { BoardSize, Piece, SquareEffect } from '../game/types';

type Props = {
    board: Piece[];
    effects?: SquareEffect[];
    size?: BoardSize;
    check: boolean;
    flipped: boolean;
    selected: number | null;
    firstTarget: number | null;
    targets: Set<number>;
    spellActive: boolean;
    lastAction: string;
    onSquare: (square: number) => void;
};

export function ChessBoard({ board, effects = [], size = standardBoard, check, flipped, selected, firstTarget, targets, spellActive, lastAction, onSquare }: Props) {
    const { width, height, originFile, originRank } = size;
    const fields = new Map(effects.map(effect => [effect.square, effect]));
    const pieces = new Map(board.map(piece => [piece.square, piece]));
    // Точное сравнение подписей отличает, например, a1 от a10 на расширенной доске.
    const lastMove = lastAction.match(/([a-z]+\d+)\s→\s([a-z]+\d+)/);
    return <div className={`board ${check ? 'in-check' : ''}`} role="group"
        style={{ gridTemplateColumns: `repeat(${width}, minmax(0, 1fr))`, aspectRatio: `${width} / ${height}` }}
        aria-label={`Доска ${width} на ${height}, ${flipped ? 'чёрные' : 'белые'} снизу`}>
        {boardOrder(size, flipped).map(s => {
            const rank = Math.floor(s / width), file = s % width;
            const label = squareName(s, width);
            const effect = fields.get(s);
            const p = pieces.get(s), legal = targets.has(s), chosen = selected === s || firstTarget === s;
            const last = lastMove?.[1] === label || lastMove?.[2] === label;
            // Вставка слева или снизу сохраняет цвета прежних клеток.
            const sage = (rank + file - originRank - originFile) % 2 === 0;
            return <button key={s}
                className={`square ${sage ? 'sage' : 'cream'} ${chosen ? 'chosen' : ''} ${legal && spellActive ? 'spell-target' : ''} ${p?.frozen ? 'frozen' : ''} ${p?.astral ? 'astral' : ''} ${last ? 'last' : ''} ${effect ? effect.type === 'WHITE_HOLE' ? 'white-hole' : 'trap-cell' : ''}`}
                onClick={() => onSquare(s)}
                aria-label={`${label}${p ? `: ${p.white ? 'белые' : 'чёрные'} ${pieceNames[p.type]}${p.astral ? ', астрал' : ''}${p.frozen ? ', заморозка' : ''}${p.shield ? ', щит' : ''}${p.boost ? ', усиление' : ''}` : ', пусто'}${effect ? `, ${effect.type === 'WHITE_HOLE' ? 'белая дыра' : effect.type === 'HIDDEN_TRAP' ? 'невидимый капкан' : 'капкан'}, осталось ${effect.turns}` : ''}`}
                aria-pressed={chosen}>
                {effect && <span className="field-marker" title={`Осталось кругов: ${effect.turns}`}>{effect.type === 'WHITE_HOLE' ? '✦' : '⌁'}{effect.turns}</span>}
                {p && <PieceIcon type={p.type} white={p.white} ghost={p.astral}/>}
                {legal && !spellActive && <span className={`move-dot ${p ? 'capture' : ''}`}/>}
                {p && <span className="badges">
                    {p.boost && <span className="badge boost" title="Усиление до любого хода">↟</span>}
                    {p.shield && <span className="badge shield" title="Щит до любого хода"><Icon type="SHIELD"/></span>}
                    {p.frozen && <span className="badge ice" title={`Заморозка: осталось ходов ${p.frozenTurns ?? 1}`}>❄</span>}
                    {p.astral && <span className="badge astral-badge" title="Оставшиеся ходы владельца до возвращения">{p.astralTurns ?? 2}</span>}
                </span>}
                {(flipped ? file === width - 1 : file === 0) && <span className="rank-label">{rank + 1}</span>}
                {(flipped ? rank === height - 1 : rank === 0) && <span className="file-label">{fileName(file)}</span>}
            </button>;
        })}
    </div>;
}
