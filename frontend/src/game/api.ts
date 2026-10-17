import type { State } from './types';
export type CommandPath = 'new' | 'action' | 'ai' | 'scout' | 'draw' | 'save' | 'load' | 'import';
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
