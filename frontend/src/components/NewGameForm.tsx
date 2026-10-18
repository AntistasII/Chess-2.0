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
    { id: 'chatgpt', icon: '✦', name: 'ChatGPT', description: 'Соперник через OpenAI API' },
    { id: 'human', icon: '♙', name: 'Вдвоём', description: 'Два игрока на одном устройстве' },
];

export function NewGameForm({ mode, difficulty, busy, networkOnly = false, apiKey = '', openAiConfigured = false, apiKeyInputSupported = true,
    error, setMode, setDifficulty, setApiKey, onStart }: Props) {
    return <form className="new-game-form" onSubmit={event => { event.preventDefault(); void onStart(); }}>
        <span className="new-game-eyebrow">НОВАЯ ПАРТИЯ</span>
        <h2>{networkOnly ? 'Игра с другом' : 'С кем играем?'}</h2>
        {networkOnly ? <div className="network-game-note">
            <span className="mode-symbol" aria-hidden="true">♙</span>
            <div><strong>Два игрока</strong><p>Владелец играет белыми, друг — чёрными. Каждый входит по своему коду и видит только свои карты.</p></div>
        </div> : <fieldset className="mode-options" disabled={busy}>
            <legend className="visually-hidden">Выберите соперника</legend>
            {modes.map(option => <label key={option.id} className={`mode-option ${mode === option.id ? 'is-active' : ''}`}>
                <span className="mode-symbol" aria-hidden="true">{option.icon}</span>
                <span className="mode-copy"><strong>{option.name}</strong><span>{option.description}</span></span>
                <input type="radio" name="opponent" value={option.id} checked={mode === option.id}
                    onChange={() => setMode(option.id)}/>
            </label>)}
        </fieldset>}
        {!networkOnly && mode === 'ai' && <label className="new-game-field">Сложность
            <select value={difficulty} disabled={busy} onChange={event => setDifficulty(event.target.value)}>
                {Object.entries(difficultyLabels).map(([key, name]) => <option value={key} key={key}>{name}</option>)}
            </select>
        </label>}
        {!networkOnly && mode === 'chatgpt' && <div className="api-key-settings">
            <label className="new-game-field">API-ключ OpenAI
                <input type="password" autoComplete="off" spellCheck={false} maxLength={1024} disabled={busy || !apiKeyInputSupported}
                    value={apiKey} onChange={event => setApiKey(event.target.value)} required={!openAiConfigured}
                    placeholder={openAiConfigured ? 'Новый ключ (необязательно)' : 'Вставьте API-ключ'}/>
            </label>
            {!apiKeyInputSupported && <p className="new-game-error" role="alert">Запущена старая версия Java-сервера, которая не принимает ключ из этого окна. Остановите Server, пересоберите проект и запустите Server заново. Затем обновите страницу.</p>}
            <p className="new-game-note">{openAiConfigured ? 'Ключ уже настроен — при желании можно указать другой. ' : ''}Ключ хранится только в памяти сервера, не попадает в файл партии. Запросы OpenAI оплачиваются отдельно.</p>
        </div>}
        <p className="new-game-note">Начинаем без карт. Взять или применить карту можно вместо хода.</p>
        {error && <p className="new-game-error" role="alert">{error}</p>}
        <button type="submit" className="primary new-game-start" disabled={busy || (!networkOnly && mode === 'chatgpt' && (!apiKeyInputSupported || (!openAiConfigured && !apiKey.trim())))}>
            {busy ? 'Создаём партию…' : 'Начать партию'}<span aria-hidden="true">→</span>
        </button>
    </form>;
}
