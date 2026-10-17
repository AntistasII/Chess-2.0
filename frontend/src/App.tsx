import { useState } from 'react';
import { useGame } from './hooks/useGame';
import { difficultyLabels } from './game/catalog';
import { Icon } from './components/Icon';
import { ChessBoard } from './components/ChessBoard';
import { CardHand } from './components/CardHand';
import { TurnDialogs } from './components/TurnDialogs';
import { GameRules } from './components/GameRules';
import { NewGameForm } from './components/NewGameForm';
export function App() {
    const { game, error, notice, busy, covered, setCovered, flipped, setFlipped, selected, cardId, firstTarget, choices, setChoices, activeCard, cardActions, canAct, targets, visibleWhite, status, hint, resetSelection, act, execute, clickSquare, selectCard, downloadGame, uploadGame, restartGame, } = useGame();
    const [modal, setModal] = useState<'new' | 'rules' | null>(null);
    const [selectedMode, setMode] = useState<string | null>(null);
    const [gameOptions, setGameOptions] = useState<Record<string, { handLimit?: number; difficulty?: string }>>({});
    const [loginCode, setLoginCode] = useState('');
    const [loginError, setLoginError] = useState('');
    const [loggingIn, setLoggingIn] = useState(false);
    if (!game)
        return <main className="loading"><h1>Шахматы с картами</h1>
        <p>{error || 'Подключаемся к игре…'}</p>
        {error && <form onSubmit={async event => {
            event.preventDefault(); setLoggingIn(true); setLoginError('');
            try {
                const response = await fetch('/api/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ code: loginCode }) });
                const data = await response.json();
                if (!response.ok) throw new Error(data.error || 'Не удалось войти');
                location.reload();
            } catch (e) { setLoginError(e instanceof Error ? e.message : 'Ошибка входа'); }
            finally { setLoggingIn(false); }
        }}>
            <label>Код владельца или приглашения
                <input type="password" autoComplete="off" value={loginCode} onChange={event => setLoginCode(event.target.value)} required />
            </label>
            <button className="primary" disabled={loggingIn}>Войти</button>
            {loginError && <p role="alert">{loginError}</p>}
        </form>}
        <button onClick={() => location.reload()}>Повторить</button>
    </main>;
    const mode = selectedMode ?? game.defaultMode;
    const defaults = game.newGameDefaults[mode];
    const limit = gameOptions[mode]?.handLimit ?? defaults.handLimit;
    const difficulty = gameOptions[mode]?.difficulty ?? defaults.difficulty;
    const setLimit = (handLimit: number) => setGameOptions(previous => ({
        ...previous, [mode]: { ...previous[mode], handLimit },
    }));
    const setDifficulty = (difficulty: string) => setGameOptions(previous => ({
        ...previous, [mode]: { ...previous[mode], difficulty },
    }));
    return <div className="app">
        <header className="topbar"><a className="brand" href="/" aria-label="Шахматы с картами"><span className="brand-mark">♞</span><span>Шахматы <span className="brand-light">с картами</span></span></a>
            <nav>
                {!(game.mode === 'network' && game.playerWhite === false) && <>
                <button className="quiet" disabled={busy} onClick={() => void downloadGame()}>Сохранить в файл</button>
                <label className="quiet" style={{ cursor: busy ? 'default' : 'pointer' }}>
                    Загрузить из файла
                    <input type="file" accept=".json,application/json" disabled={busy} style={{ display: 'none' }} onChange={event => {
                        const file = event.target.files?.[0];
                        event.target.value = '';
                        if (file && window.confirm('Загрузить партию из файла? Текущая позиция будет заменена.')) void uploadGame(file);
                    }} />
                </label>
                <button className="quiet" disabled={busy} onClick={() => void act('save', {})}>Сохранить</button>
                <button className="quiet" disabled={busy} onClick={() => {
                    if (window.confirm('Загрузить сохранённую партию? Текущая позиция будет заменена.')) void act('load', {});
                }}>Продолжить сохранённую</button>
                <button className="quiet" onClick={() => setModal('rules')}><Icon type="book"/><span>Правила</span>
                </button>
                <button className="quiet" onClick={() => setModal('new')}><Icon type="settings"/><span>Новая игра</span>
                </button>
                {game.mode === 'network' && <button className="quiet" disabled={busy} onClick={() => {
                    if (window.confirm('Полностью перезапустить игру? Партия сбросится, оба игрока выйдут и снова введут свои коды.')) void restartGame();
                }}>Перезапустить полностью</button>}
                </>}
                {game.mode === 'network' && game.playerWhite === false && <span>Сетевая игра · вы чёрные</span>}
            </nav>
        </header>
        <main className="layout">
            <section className="board-panel" aria-label="Шахматная доска">
                <div className="player-row">
                    <div className="player-info"><span className="avatar dark">{visibleWhite ? '♚' : '♔'}</span>
                        <div>
                            <strong>{game.mode === 'chatgpt' ? 'ChatGPT' : game.mode === 'ai' ? 'Локальный ИИ' : 'Соперник'}</strong><small>{visibleWhite ? 'Чёрные' : 'Белые'}{game.mode === 'ai' ? ` · ${difficultyLabels[game.difficulty]}` : ''}</small>
                        </div>
                    </div>
                    <div className="enemy-hand" title={`${game.enemyCards} скрытых карт`}><Icon type="deck"/><span>{game.enemyCards}</span></div>
                </div>
                <ChessBoard effects={covered ? game.squareEffects?.filter(e => e.type !== 'HIDDEN_TRAP') : game.squareEffects} board={game.board} size={game.boardSize} check={game.check} flipped={flipped} selected={selected} firstTarget={firstTarget} targets={targets} spellActive={Boolean(activeCard)} lastAction={game.history.at(-1) ?? ''} onSquare={clickSquare}/>
                <div className="player-row bottom">
                    <div className="player-info"><span className="avatar light">{visibleWhite ? '♔' : '♚'}</span>
                        <div>
                            <strong>{game.mode !== 'human' ? 'Вы' : 'Текущий игрок'} · {visibleWhite ? 'Белые' : 'Чёрные'}</strong><small className={game.check ? 'danger' : 'turn-status'}><span className="status-dot"/>{status}
                        </small></div>
                    </div>
                    <button className="text-button" onClick={() => setFlipped(!flipped)} title="Перевернуть доску" aria-label="Перевернуть доску">↻
                    </button>
                </div>
                <div className="hint" aria-live="polite">{hint}{(selected !== null || cardId !== null) &&
            <button onClick={resetSelection}>Отменить выбор</button>}</div>
                {notice && <div className="hint" role="status">{notice}</div>}
                {error && <div className="error" role="alert">{error}{(game.mode === 'ai' || game.mode === 'chatgpt') && !game.whiteTurn && !busy &&
                <button onClick={() => void act('ai', {})}>Повторить ход соперника</button>}</div>}
                {game.result && !(game.mode === 'network' && game.playerWhite === false) &&
            <button className="primary end-button" onClick={() => setModal('new')}>Сыграть ещё</button>}
            </section>

            <aside className="sidebar">
                <section className="deck-section">
                    <div className="section-label">ОБЩАЯ КОЛОДА</div>
                    <div className="deck-row">
                        <div className="deck-art"><Icon type="deck"/></div>
                        <div className="deck-detail"><strong>{game.deckCount}
                            <span>карт</span></strong><small>{game.discardCount ? `Использовано: ${game.discardCount}` : 'Одна колода для обоих игроков'}</small>
                            <button className="primary" disabled={!canAct || !game.actions.some(a => a.id === 'draw')} onClick={() => void act('action', { actionId: 'draw' })}>Взять карту <span>＋</span>
                            </button>
                            <small className="under-button">Вместо хода</small></div>
                    </div>
                    {game.check &&
            <p className="muted small">При шахе добор запрещён.</p>}{game.hand.length >= game.handLimit &&
            <p className="muted small">Рука заполнена — сначала примените карту.</p>}</section>
                <CardHand game={game} covered={covered} canAct={canAct} cardId={cardId} activeCard={activeCard} cardActions={cardActions} hint={hint} selectCard={selectCard} execute={execute} onScout={id => void act('scout', { cardId: id })}/>
                <section className="history-section">
                    <div className="section-title"><h2>Последние действия</h2><span>{game.ply}</span></div>
                    {game.history.length === 0 ?
            <p className="muted small">Партия начинается. Белые ходят первыми.</p> :
            <ol>{game.history.slice(-6).map((line, i) => <li key={`${game.ply}-${i}`}><span className="history-number">{Math.max(1, game.ply - 5) + i}</span>{line}</li>)}</ol>}
                </section>
                <div className="sidebar-footer"><span className="local-dot"/>{game.mode === 'chatgpt' ? 'ChatGPT · OpenAI API' : game.mode === 'network' ? 'Сетевая игра · по IP' : 'Локальная игра · без интернета'}{game.canClaimDraw && canAct && <button onClick={() => void act('draw', {})}>Заявить ничью</button>}
                </div>
            </aside>
        </main>

        <TurnDialogs game={game} covered={covered} busy={busy} choices={choices} execute={execute} onAction={id => void act('action', { actionId: id })} onCancelChoice={() => setChoices([])} onReady={() => { setCovered(false); setFlipped(!game.whiteTurn); }}/>
        {modal && <div className="overlay" onClick={() => setModal(null)}>
            <div className={`dialog ${modal === 'rules' ? 'rules-dialog' : ''}`} onClick={e => e.stopPropagation()}>
                <button className="close" onClick={() => setModal(null)} aria-label="Закрыть">×</button>
                {modal === 'new' ? <NewGameForm mode={mode} limit={limit} difficulty={difficulty} busy={busy} setMode={setMode} setLimit={setLimit} setDifficulty={setDifficulty} onStart={async () => {
                    await act('new', { mode, handLimit: limit, difficulty });
                    setModal(null);
                    setFlipped(false);
                    setCovered(mode === 'human');
                }}/> : <GameRules />}</div>
        </div>}
    </div>;
}
