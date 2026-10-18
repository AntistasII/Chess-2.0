import { difficultyLabels } from '../game/catalog';

type Props = {
    mode: string;
    difficulty: string;
    busy: boolean;
    networkOnly?: boolean;
    apiKey?: string;
    openAiConfigured?: boolean;
    apiKeyInputSupported?: boolean;
    error?: string;
    setMode: (mode: string) => void;
    setDifficulty: (difficulty: string) => void;
    setApiKey: (key: string) => void;
    onStart: () => Promise<void>;
};
const modes = [
    { id: 'ai', icon: '♞', name: 'Локальный ИИ', description: 'Компьютерный соперник без интернета' },
    { id: 'chatgpt', icon: '✦', name: 'ChatGPT', description: 'Игра с ChatGPT' },
    { id: 'human', icon: '♙', name: 'Вдвоём', description: 'Два игрока на одном устройстве' },
];

export function NewGameForm({ mode, difficulty, busy, networkOnly = false, apiKey = '', openAiConfigured = false, apiKeyInputSupported = true,
    error, setMode, setDifficulty, setApiKey, onStart }: Props) {
    return <form className="new-game-form" onSubmit={event => { event.preventDefault(); void onStart(); }}>
        <span className="new-game-eyebrow">НОВАЯ ПАРТИЯ</span>
        <h2>С кем играем?</h2>
        <fieldset className="mode-options" disabled={busy}>
            <legend className="visually-hidden">Выберите соперника</legend>
            {(networkOnly ? [
                { id: 'network', icon: '♙', name: 'С другом по сети', description: 'Каждый входит по своему коду и видит только свои карты' },
                modes[1],
            ] : modes).map(option => <label key={option.id} className={`mode-option ${mode === option.id ? 'is-active' : ''}`}>
                <span className="mode-symbol" aria-hidden="true">{option.icon}</span>
                <span className="mode-copy"><strong>{option.name}</strong><span>{option.description}</span></span>
                <input type="radio" name="opponent" value={option.id} checked={mode === option.id}
                    onChange={() => setMode(option.id)}/>
            </label>)}
        </fieldset>
        {!networkOnly && mode === 'ai' && <label className="new-game-field">Сложность
            <select value={difficulty} disabled={busy} onChange={event => setDifficulty(event.target.value)}>
                {Object.entries(difficultyLabels).map(([key, name]) => <option value={key} key={key}>{name}</option>)}
            </select>
        </label>}
        {networkOnly && mode === 'chatgpt' && <div className="api-key-settings">
            <label className="new-game-field">Ключ ChatGPT
                <input type="password" autoComplete="off" spellCheck={false} maxLength={1024} disabled={busy || !apiKeyInputSupported}
                    value={apiKey} onChange={event => setApiKey(event.target.value)} required
                    placeholder="Вставьте ключ"/>
            </label>
            {!apiKeyInputSupported && <p className="new-game-error" role="alert">Не удалось подключить ChatGPT. Обновите страницу и попробуйте ещё раз.</p>}
            <p className="new-game-note">Для игры нужен ключ ChatGPT. Использование оплачивается отдельно.</p>
        </div>}
        {!networkOnly && mode === 'chatgpt' && !openAiConfigured && <p className="new-game-note">ChatGPT пока недоступен. Выберите другого соперника.</p>}
        <p className="new-game-note">Начинаем без карт. Взять или применить карту можно вместо хода.</p>
        {error && <p className="new-game-error" role="alert">{error}</p>}
        <button type="submit" className="primary new-game-start" disabled={busy || (networkOnly && mode === 'chatgpt' && (!apiKeyInputSupported || (!openAiConfigured && !apiKey.trim())))}>
            {busy ? 'Создаём партию…' : 'Начать партию'}<span aria-hidden="true">→</span>
        </button>
    </form>;
}
