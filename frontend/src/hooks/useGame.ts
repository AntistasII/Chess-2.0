import { useEffect, useRef, useState } from 'react';
import { request, requestOpponent, type CommandPath } from '../game/api';
import { gameStatus, selectionHint } from '../game/catalog';
import type { Action, Card, State } from '../game/types';
export function useGame() {
    const [game, setGame] = useState<State | null>(null);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [busy, setBusy] = useState(false);
    const [selected, setSelected] = useState<number | null>(null);
    const [cardId, setCardId] = useState<number | null>(null);
    const [firstTarget, setFirstTarget] = useState<number | null>(null);
    const [choices, setChoices] = useState<Action[]>([]);
    const [covered, setCovered] = useState(false);
    const [flipped, setFlipped] = useState(false);
    const aiInFlight = useRef(false);
    const resetSelection = () => {
        setSelected(null);
        setCardId(null);
        setFirstTarget(null);
        setChoices([]);
    };
    useEffect(() => {
        request('state').then(state => {
            setGame(state);
            if (state.mode === 'human')
                setCovered(true);
        }).catch(e => setError(e.message));
    }, []);
    useEffect(() => {
        if (!game || (game.mode === 'human' || game.mode === 'network') || game.whiteTurn || game.result || aiInFlight.current)
            return;
        const timer = setTimeout(() => {
            aiInFlight.current = true;
            setBusy(true);
            setError('');
            requestOpponent({ version: game.version }).then(setGame).catch(e => setError(e.message)).finally(() => {
                aiInFlight.current = false;
                setBusy(false);
            });
        }, 180);
        return () => clearTimeout(timer);
    }, [game]);
    useEffect(() => {
        if (game?.mode !== 'network' && game !== null) return;
        if (busy) return;
        let cancelled = false;
        let timer: ReturnType<typeof setTimeout>;
        async function poll() {
            try {
                const next = await request('state');
                if (!cancelled) {
                    setGame(previous => previous?.version === next.version && previous.mode === next.mode
                        && previous.playerWhite === next.playerWhite ? previous : next);
                    setError('');
                }
            } catch (e) { if (!cancelled) setError(e instanceof Error ? e.message : 'Нет связи с сервером'); }
            if (!cancelled) timer = setTimeout(poll, 1000);
        }
        timer = setTimeout(poll, 1000);
        return () => { cancelled = true; clearTimeout(timer); };
    }, [game?.mode, busy]);
    useEffect(() => {
        if (game?.mode === 'network') { setFlipped(game.playerWhite === false); setCovered(false); resetSelection(); }
    }, [game?.mode, game?.playerWhite]);
    async function act(path: CommandPath, body: object) {
        if (!game || busy)
            return;
        setBusy(true);
        setError('');
        setNotice('');
        try {
            const payload = { ...body, version: game.version };
            const next = await (path === 'ai' ? requestOpponent(payload) : request(path, payload));
            resetSelection();
            if (path === 'save') setNotice('Партия сохранена на диске.');
            else {
                setGame(next);
                if (path === 'load' || path === 'import') {
                    setCovered(next.mode === 'human');
                    setFlipped(false);
                    setNotice('Сохранённая партия загружена.');
                }
            }
            if (next.mode === 'human' && next.whiteTurn !== game.whiteTurn && !next.result)
                setCovered(true);
        }
        catch (e) {
            setError(e instanceof Error ? e.message : 'Ошибка соединения');
            try {
                const latest = await request('state');
                if (latest.version !== game.version) setGame(latest);
            }
            catch { /* Keep the last visible state. */
            }
        }
        finally {
            setBusy(false);
        }
    }
    async function downloadGame() {
        if (!game || busy) return;
        setBusy(true); setError(''); setNotice('');
        try {
            const response = await fetch('/api/export', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ version: game.version }),
            });
            if (!response.ok) throw new Error((await response.json()).error || 'Не удалось сохранить файл');
            const url = URL.createObjectURL(await response.blob());
            const link = document.createElement('a');
            link.href = url;
            link.download = `chess-${new Date().toISOString().replace(/[:.]/g, '-')}.json`;
            document.body.appendChild(link); link.click(); link.remove();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
            setNotice('Файл партии отправлен на скачивание.');
        } catch (e) { setError(e instanceof Error ? e.message : 'Ошибка сохранения'); }
        finally { setBusy(false); }
    }
    async function uploadGame(file: File) {
        if (!game || busy) return;
        try {
            if (file.size > 9_900_000) throw new Error('Файл сохранения слишком большой.');
            const save = JSON.parse(await file.text());
            await act('import', { save });
        } catch (e) { setError(e instanceof SyntaxError ? 'Файл содержит некорректный JSON.'
            : e instanceof Error ? e.message : 'Ошибка загрузки файла'); }
    }
    function execute(actions: Action[]) {
        if (actions.length === 1)
            void act('action', { actionId: actions[0].id });
        else if (actions.length > 1)
            setChoices(actions);
    }
    const activeCard = game?.hand.find(c => c.id === cardId);
    const cardActions = game?.actions.filter(a => a.kind === 'card' && a.cardId === cardId) ?? [];
    const canAct = Boolean(game && !busy && !game.result && !covered && (game.mode === 'network' ? game.playerWhite === game.whiteTurn : !(game.mode !== 'human' && !game.whiteTurn)) && game.scouting.length === 0);
    const targets = new Set(activeCard
        ? cardActions.filter(a => firstTarget === null || a.to === firstTarget).map(a => firstTarget === null ? a.to : a.other)
        : (game?.actions ?? []).filter(a => a.kind === 'move' && a.from === selected).map(a => a.to));
    const visibleWhite = game?.mode === 'network' ? game.playerWhite !== false : game?.mode === 'human' ? game.whiteTurn : true;
    const pieces = new Map((game?.board ?? []).map(p => [p.square, p]));
    const status = gameStatus(game, busy);
    function clickSquare(s: number) {
        if (!game || !canAct)
            return;
        if (activeCard) {
            if (!targets.has(s))
                return;
            if (activeCard.type === 'SWAP' && firstTarget === null) {
                setFirstTarget(s);
                return;
            }
            execute(cardActions.filter(a => firstTarget === null ? a.to === s : a.to === firstTarget && a.other === s));
            return;
        }
        if (selected !== null && targets.has(s)) {
            execute(game.actions.filter(a => a.kind === 'move' && a.from === selected && a.to === s));
            return;
        }
        const p = pieces.get(s);
        setSelected(p && p.white === game.whiteTurn && !p.astral && !p.frozen ? s : null);
    }
    function selectCard(card: Card) {
        if (!canAct)
            return;
        setSelected(null);
        setFirstTarget(null);
        setChoices([]);
        setCardId(cardId === card.id ? null : card.id);
    }
    const hint = game?.mode === 'network' && game.playerWhite !== game.whiteTurn ? 'Ожидаем ход соперника…' : selectionHint(activeCard, firstTarget, selected);
    return {
        game, error, notice, busy, covered, setCovered, flipped, setFlipped,
        selected, cardId, firstTarget, choices, setChoices, activeCard,
        cardActions, canAct, targets, visibleWhite, status, hint,
        resetSelection, act, execute, clickSquare, selectCard, downloadGame, uploadGame,
    };
}
