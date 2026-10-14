import { Icon } from './Icon';
import { descriptions } from '../game/catalog';
import type { Action, Card, State } from '../game/types';
type Props = {
    game: State;
    covered: boolean;
    canAct: boolean;
    cardId: number | null;
    activeCard: Card | undefined;
    cardActions: Action[];
    hint: string;
    selectCard: (card: Card) => void;
    execute: (actions: Action[]) => void;
    onScout: (cardId: number) => void;
};
export function CardHand({ game, covered, canAct, cardId, activeCard, cardActions, hint, selectCard, execute, onScout }: Props) {
    return <section className="hand-section">
                    <div className="section-title"><h2>Ваши карты</h2>
                        <span>{covered ? '—' : game.hand.length} / {game.handLimit}</span></div>
                    {covered ?
            <div className="empty-hand">Карты скрыты до передачи хода.</div> : game.hand.length === 0 ?
            <div className="empty-hand"><Icon type="deck"/><strong>Пока нет карт</strong><p>Возьмите
                                карту вместо хода,<br />чтобы сохранить её на потом.</p></div> :
            <div className="card-list">{game.hand.map(card => {
                    const usable = game.actions.some(a => a.cardId === card.id);
                    const exchangeTarget = activeCard?.type === 'EXCHANGE' && cardActions.some(a => a.other === card.id);
                    return <button key={card.id} className={`card ${card.id === cardId ? 'selected' : ''} ${exchangeTarget ? 'exchange-target' : ''} ${!usable ? 'unavailable' : ''}`} onClick={() => exchangeTarget ? execute(cardActions.filter(a => a.other === card.id)) : selectCard(card)} disabled={!canAct} title={descriptions[card.type]}><span className={`card-icon ${card.type.toLowerCase()}`}><Icon type={card.type}/></span><strong>{card.name}</strong>
                                    <p>{descriptions[card.type]}</p><span className="card-footer">{exchangeTarget ? 'Обменять эту карту' : usable ? 'Вместо хода' : 'Нет доступной цели'}</span>
                                </button>;
                })}</div>}
                    {activeCard && <div className="card-help"><strong>{activeCard.name}</strong>
                        <p>{descriptions[activeCard.type]}</p>{cardActions.length === 0 ?
                <small>Сейчас нет допустимого применения.</small> : activeCard.type === 'SCOUT' ?
                <button className="primary" onClick={() => onScout(activeCard.id)}>Применить
                                    разведку</button> : <small>{hint}</small>}</div>}
                </section>;
}
