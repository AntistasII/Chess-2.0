import { useEffect, useRef } from 'react';
import type { State } from '../game/types';

/** Один щелчок реле с низким корпусным резонансом и коротким затуханием. */
function turnSignal(context: AudioContext): AudioBuffer {
    const buffer = context.createBuffer(1, Math.ceil(context.sampleRate * .095), context.sampleRate);
    const samples = buffer.getChannelData(0);
    const smoothing = 1 - Math.exp(-2 * Math.PI * 2100 / context.sampleRate);
    let seed = 12345, low = 0;
    for (let i = 0; i < samples.length; i++) {
        const t = i / context.sampleRate;
        seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
        const noise = seed / 0x100000000 * 2 - 1;
        low += smoothing * (noise - low);
        const attack = Math.min(1, t / .0008);
        const tail = Math.min(1, (samples.length - 1 - i) / (context.sampleRate * .008));
        samples[i] = attack * tail * (
            .32 * low * Math.exp(-t * 220)
            + .12 * Math.sin(2 * Math.PI * 1300 * t) * Math.exp(-t * 190)
            + .24 * Math.sin(2 * Math.PI * 240 * t) * Math.exp(-t * 65)
            + .13 * Math.sin(2 * Math.PI * 120 * t) * Math.exp(-t * 55));
    }
    return buffer;
}

/** Один глубокий щелчок поворотника на новый свой ход; опросы сервера и выбор фигуры не звучат. */
export function useTurnSound(game: State | null) {
    const audio = useRef<AudioContext | null>(null);
    const sound = useRef<AudioBuffer | null>(null);
    const announced = useRef('');
    const turn = useRef<{ key: string; mine: boolean }>({ key: '', mine: false });
    const click = useRef(() => {});
    click.current = () => {
        const context = audio.current;
        if (!context || context.state !== 'running' || !turn.current.mine || announced.current === turn.current.key) return;
        announced.current = turn.current.key;
        sound.current ??= turnSignal(context);
        const source = context.createBufferSource();
        source.buffer = sound.current;
        source.connect(context.destination);
        source.onended = () => source.disconnect();
        source.start();
    };
    useEffect(() => {
        const unlock = () => {
            try {
                audio.current ??= new AudioContext();
                void audio.current.resume().then(() => click.current()).catch(() => {});
            } catch { /* Браузер без Web Audio сохраняет обычную игру. */ }
        };
        window.addEventListener('pointerdown', unlock);
        window.addEventListener('keydown', unlock);
        return () => {
            window.removeEventListener('pointerdown', unlock);
            window.removeEventListener('keydown', unlock);
            void audio.current?.close().catch(() => {}); audio.current = null; sound.current = null;
        };
    }, []);
    useEffect(() => {
        const mine = Boolean(game && !game.result && (game.mode === 'human'
            || (game.mode === 'network' ? game.playerWhite === game.whiteTurn : game.whiteTurn)));
        const key = game ? `${game.mode}:${game.ply}:${game.whiteTurn}` : '';
        if (!game) announced.current = '';
        // После загрузки/новой партии тот же номер хода снова может требовать уведомления.
        if (key !== turn.current.key) announced.current = '';
        turn.current = { key, mine };
        click.current();
    }, [game?.mode, game?.ply, game?.whiteTurn, game?.playerWhite, game?.result]);
}
