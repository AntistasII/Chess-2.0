import { Icon } from './Icon';
import type { State } from '../game/types';

type Props = { game: State; canAct: boolean; onDraw: () => void };

export function DrawPile({ game, canAct, onDraw }: Props) {
    return <section className="draw-pile" aria-label="Общая колода">
        <div className="pile-heading">
            <h2>Общая колода</h2>
            <span className="pile-discard">В сбросе <b>{game.discardCount}</b></span>
        </div>
        <div className="pile-content">
            <div className="pile-stock" aria-label={`В колоде осталось карт: ${game.deckCount}`}>
                <span className="pile-icon"><Icon type="deck"/></span>
                <div className="pile-counter"><strong>{game.deckCount}</strong><span>карт в колоде</span></div>
            </div>
            <button className="primary pile-draw" disabled={!canAct || !game.actions.some(a => a.id === 'draw')}
                onClick={onDraw} aria-label="Взять карту вместо хода">
                <span className="pile-button-label"><span>Взять карту</span><span>Вместо хода</span></span>
                <span className="pile-plus" aria-hidden="true">＋</span>
            </button>
        </div>
        {game.check && <p className="pile-notice">При шахе добор запрещён.</p>}
        {game.hand.length >= game.handLimit && <p className="pile-notice">Рука заполнена — сначала примените карту.</p>}
    </section>;
}
