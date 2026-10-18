import type { State } from './types';
export type CommandPath = 'new' | 'action' | 'ai' | 'scout' | 'draw' | 'resign' | 'save' | 'load' | 'import';
/** Совместимость со сборкой, где ввод ключа появился раньше явного флага возможностей. */
export function supportsApiKeyInput(state: State): boolean {
    return state.apiKeyInputSupported ?? (typeof state.openAiConfigured === 'boolean' && !state.networkOnly);
}

export class ApiError extends Error {
    constructor(message: string, public readonly status: number) { super(message); }
}

export async function request(path: CommandPath | 'state', body?: object): Promise<State> {
    const response = await fetch(`/api/${path}`, body === undefined ? undefined : {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
    const data = await response.json().catch(() => ({ error: `Ошибка сервера (HTTP ${response.status})` }));
    if (!response.ok)
        throw new ApiError(data.error || 'Не удалось выполнить действие', response.status);
    return data;
}

/** Число повторов приходит из серверной конфигурации; версия позиции остаётся прежней. */
export async function requestOpponent(
    body: object,
    maxRetries: number,
    send: (body: object) => Promise<State> = body => request('ai', body),
    pause: (ms: number) => Promise<void> = ms => new Promise(resolve => setTimeout(resolve, ms)),
): Promise<State> {
    for (let retry = 0; ; retry++) {
        try { return await send(body); }
        catch (error) {
            if (!(error instanceof ApiError) || error.status !== 502 || retry >= maxRetries) throw error;
            await pause(Math.min(1000 * (retry + 1), 4000));
        }
    }
}

/** Повторяем сдачу только в той же партии, сохраняя сторону игрока. */
export async function requestResignation(
    position: State,
    send: (body: object) => Promise<State> = body => request('resign', body),
    read: () => Promise<State> = () => request('state'),
): Promise<State> {
    if (!position.gameId) throw new ApiError('Обновите сервер и страницу перед сдачей: у позиции нет идентификатора партии.', 409);
    const payload = (state: State) => ({ version: state.version, gameId: state.gameId });
    try { return await send(payload(position)); }
    catch (error) {
        if (!(error instanceof ApiError) || error.status !== 409) throw error;
        const latest = await read();
        if (latest.gameId !== position.gameId || latest.mode !== position.mode
            || latest.playerWhite !== position.playerWhite
            || (position.mode === 'human' && latest.whiteTurn !== position.whiteTurn))
            throw new ApiError('Партия или игрок изменились. Сдача отменена. Обновите страницу, чтобы увидеть актуальную игру.', 409);
        if (latest.result) return latest;
        return send(payload(latest));
    }
}
