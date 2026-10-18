import { useState, type CSSProperties } from 'react';
import { useGame } from './hooks/useGame';
import { difficultyLabels } from './game/catalog';
import { Icon } from './components/Icon';
import { ChessBoard } from './components/ChessBoard';
import { CardHand } from './components/CardHand';
import { DrawPile } from './components/DrawPile';
import { TurnDialogs } from './components/TurnDialogs';
import { GameRules } from './components/GameRules';
import { NewGameForm } from './components/NewGameForm';
import { GameMenu } from './components/GameMenu';
import { useTurnSound } from './hooks/useTurnSound';
import { useTheme } from './hooks/useTheme';
import { supportsApiKeyInput } from './game/api';
import { ThemeSwitcher } from './components/ThemeSwitcher';
export function App() {
    const { game, error, notice, busy, covered, setCovered, flipped, setFlipped, selected, cardId, firstTarget, choices, setChoices, activeCard, cardActions, canAct, targets, visibleWhite, status, hint, resetSelection, act, execute, clickSquare, selectCard, downloadGame, uploadGame, restartGame, } = useGame();
    useTurnSound(game);
    const { theme, setTheme } = useTheme();
    const [modal, setModal] = useState<'new' | 'rules' | null>(null);
    const [selectedMode, setMode] = useState<string | null>(null);
    const [gameOptions, setGameOptions] = useState<Record<string, { difficulty?: string }>>({});
    const [apiKey, setApiKey] = useState('');
    const closeModal = () => { setModal(null); setApiKey(''); };
    const [loginCode, setLoginCode] = useState('');
    const [loginError, setLoginError] = useState('');
    const [loggingIn, setLoggingIn] = useState(false);
    if (!game)
        return <main className="loading"><div className="loading-theme"><ThemeSwitcher theme={theme} onChange={setTheme}/></div><h1>Шахматы с картами</h1>
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
    const localMode = selectedMode ?? game.defaultMode;
    const allowedModes = game.networkOnly ? ['network', 'chatgpt'] : ['ai', 'chatgpt', 'human'];
    const mode = allowedModes.includes(localMode) ? localMode : allowedModes[0];
    const defaults = game.newGameDefaults[mode];
    const difficulty = gameOptions[mode]?.difficulty ?? defaults.difficulty;
    const setDifficulty = (difficulty: string) => setGameOptions(previous => ({
        ...previous, [mode]: { ...previous[mode], difficulty },
    }));
    return <div className="app">
        <header className="topbar">
            <nav aria-label="Управление игрой">
                <GameMenu busy={busy} guest={game.mode === 'network' && game.playerWhite === false}
                    onNew={() => setModal('new')} onRules={() => setModal('rules')}
                    onContinue={() => { if (window.confirm('Загрузить сохранённую партию? Текущая позиция будет заменена.')) void act('load', {}); }}
                    onSave={() => void act('save', {})} onDownload={() => void downloadGame()}
                    onUpload={file => void uploadGame(file)} onReset={() => {
                        if (window.confirm('Полностью перезапустить игру? Партия сбросится, игроки выйдут и снова введут свои коды.')) void restartGame();
                    }}/>
            </nav>
            <a className="brand" href="/" aria-label="Шахматы с картами"><span className="brand-mark">♞</span><span>Шахматы <span className="brand-light">с картами</span></span></a>
            <ThemeSwitcher theme={theme} onChange={setTheme}/>
            {game.mode === 'network' && <span className="network-seat">Вы {game.playerWhite === false ? 'чёрные' : 'белые'}</span>}
        </header>
        <main className="layout" style={{ '--board-ratio': game.boardSize.width / game.boardSize.height } as CSSProperties}>
            <section className="board-panel" aria-label="Шахматная доска">
                <div className="player-row">
                    <div className="player-info"><span className="avatar dark">{visibleWhite ? '♚' : '♔'}</span>
                        <div>
                            <strong>{game.mode === 'chatgpt' ? 'ChatGPT' : game.mode === 'ai' ? 'Локальный ИИ' : 'Соперник'}</strong><small>{visibleWhite ? 'Чёрные' : 'Белые'}{game.mode === 'ai' ? ` · ${difficultyLabels[game.difficulty]}` : ''}</small>
                        </div>
                    </div>
                    <div className="enemy-hand" title={`${game.enemyCards} скрытых карт`}><Icon type="deck"/><span>{game.enemyCards}</span></div>
                </div>
                <div className="board-space"><ChessBoard effects={covered ? game.squareEffects?.filter(e => e.type !== 'HIDDEN_TRAP') : game.squareEffects} board={game.board} size={game.boardSize} check={game.check} flipped={flipped} selected={selected} firstTarget={firstTarget} targets={targets} spellActive={Boolean(activeCard)} lastAction={game.history.at(-1) ?? ''} onSquare={clickSquare}/></div>
                <div className="player-row bottom">
                    <div className="player-info"><span className="avatar light">{visibleWhite ? '♔' : '♚'}</span>
                        <div>
                            <strong>{game.mode !== 'human' ? 'Вы' : 'Текущий игрок'} · {visibleWhite ? 'Белые' : 'Чёрные'}</strong><small className={game.check ? 'danger' : 'turn-status'}><span className="status-dot"/>{status}
                        </small></div>
                    </div>
                    <div className="player-actions">
                        {!game.result && !covered && <button className="resign-button" disabled={busy} onClick={() => {
                            if (window.confirm('Сдаться? Сопернику будет засчитана победа.')) void act('resign', {});
                        }}>Сдаюсь</button>}
                    <button className="text-button" onClick={() => setFlipped(!flipped)} title="Перевернуть доску" aria-label="Перевернуть доску">↻
                    </button>
                    </div>
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
                <DrawPile game={game} canAct={canAct} onDraw={() => void act('action', { actionId: 'draw' })}/>
                <CardHand game={game} covered={covered} canAct={canAct} cardId={cardId} activeCard={activeCard} cardActions={cardActions} hint={hint} selectCard={selectCard} execute={execute} onScout={id => void act('scout', { cardId: id })}/>
                <section className="history-section">
                    <div className="section-title"><h2>Последние действия</h2><span>{game.ply}</span></div>
                    {game.history.length === 0 ?
            <p className="muted small">Партия начинается. Белые ходят первыми.</p> :
            <ol>{game.history.slice(-6).reverse().map((line, i) => <li key={`${game.ply}-${i}`}><span className="history-number">{game.ply - i}</span>{line}</li>)}</ol>}
                </section>
                <div className="sidebar-footer"><span className="local-dot"/>{game.mode === 'chatgpt' ? 'ChatGPT · OpenAI API' : game.mode === 'network' ? 'Сетевая игра · по IP' : 'Локальная игра · без интернета'}{game.canClaimDraw && canAct && <button onClick={() => void act('draw', {})}>Заявить ничью</button>}
                </div>
            </aside>
        </main>

        <TurnDialogs game={game} covered={covered} busy={busy} choices={choices} execute={execute} onAction={id => void act('action', { actionId: id })} onCancelChoice={() => setChoices([])} onReady={() => { setCovered(false); setFlipped(!game.whiteTurn); }}/>
        {modal && <div className="overlay" onClick={closeModal}>
            <div className={`dialog ${modal === 'rules' ? 'rules-dialog' : ''}`} onClick={e => e.stopPropagation()}>
                <button className="close" onClick={closeModal} aria-label="Закрыть">×</button>
                {modal === 'new' ? <NewGameForm mode={mode} difficulty={difficulty} busy={busy} networkOnly={game.networkOnly}
                    apiKey={apiKey} setApiKey={setApiKey} openAiConfigured={game.openAiConfigured} apiKeyInputSupported={supportsApiKeyInput(game)} error={error}
                    setMode={setMode} setDifficulty={setDifficulty} onStart={async () => {
                    const started = await act('new', { mode, difficulty, ...(game.networkOnly && mode === 'chatgpt' && apiKey.trim() ? { apiKey: apiKey.trim() } : {}) });
                    if (started) {
                        closeModal();
                        setFlipped(false);
                        setCovered(mode === 'human');
                    }
                }}/> : <GameRules />}</div>
        </div>}
    </div>;
}
