import { Icon } from './Icon';
import { PieceIcon } from './PieceIcon';
import { pieceNames } from '../game/catalog';
import type { Action, State } from '../game/types';
type Props = {
    game: State;
    covered: boolean;
    busy: boolean;
    choices: Action[];
    execute: (actions: Action[]) => void;
    onReady: () => void;
    onAction: (actionId: string) => void;
    onCancelChoice: () => void;
};
export function TurnDialogs({ game, covered, busy, choices, execute, onReady, onAction, onCancelChoice }: Props) {
    return <>
        {covered && !game.result && <div className="overlay">
            <div className="dialog pass-dialog"><span className="dialog-symbol">{game.whiteTurn ? '♔' : '♚'}</span>
                <h2>Ход {game.whiteTurn ? 'белых' : 'чёрных'}</h2><p>Передайте устройство следующему игроку.<br />Карты
                    соперника остаются скрытыми.</p>
                <button className="primary" onClick={onReady}>Показать мои карты
                </button>
            </div>
        </div>}
        {!game.result && game.scouting.length > 0 && <div className="overlay">
            <div className="dialog"><h2>Разведка</h2><p>Верхние карты колоды. Первая будет взята следующей.</p>
                <div className="scout-cards">{game.scouting.map((card, i) => <div className="mini-card" key={card.id}>
                    <small>{i + 1}-я карта</small><Icon type={card.type}/><strong>{card.name}</strong></div>)}</div>
                <div className="dialog-actions">
                    <button disabled={busy} onClick={() => execute(game.actions.filter(a => a.option === 'reverse'))}>Поменять местами
                    </button>
                    <button disabled={busy} className="primary" onClick={() => execute(game.actions.filter(a => a.option === 'keep'))}>Оставить порядок
                    </button>
                </div>
            </div>
        </div>}
        {choices.length > 0 && <div className="overlay">
            <div className="dialog">
                <h2>{choices.some(a => a.option === 'castle') ? 'Выберите ход короля' : choices[0].kind === 'move' ? 'Превращение пешки' : 'Какую фигуру вернуть?'}</h2>
                <div className="promotion-options">{choices.map(a => <button key={a.id} onClick={() => onAction(a.id)}>
                    <PieceIcon type={a.option === 'castle' || !a.option ? 'K' : a.option} white={game.whiteTurn}/><span>{a.option === 'castle' ? 'Рокировка' : !a.option ? 'Усиленный ход' : pieceNames[a.option]}</span>
                </button>)}</div>
                <button className="text-button" onClick={onCancelChoice}>Назад</button>
            </div>
        </div>}
    </>;
}
