import { difficultyLabels } from '../game/catalog';

type Props = {
    mode: string;
    limit: number;
    difficulty: string;
    busy: boolean;
    setMode: (mode: string) => void;
    setLimit: (limit: number) => void;
    setDifficulty: (difficulty: string) => void;
    onStart: () => Promise<void>;
};

export function NewGameForm({ mode, limit, difficulty, busy, setMode, setLimit, setDifficulty, onStart }: Props) {
    return <>
        <div className="section-label">НОВАЯ ПАРТИЯ</div>
        <h2>С кем играем?</h2>
        <div className="segmented" style={{ flexWrap: 'wrap' }}>
            <button className={mode === 'ai' ? 'active' : ''} onClick={() => setMode('ai')}>С локальным ИИ</button>
            <button className={mode === 'chatgpt' ? 'active' : ''} onClick={() => setMode('chatgpt')}>С ChatGPT</button>
            <button className={mode === 'network' ? 'active' : ''} onClick={() => setMode('network')}>По сети</button>
            <button className={mode === 'human' ? 'active' : ''} onClick={() => setMode('human')}>Вдвоём</button>
        </div>
        {mode === 'ai' && <label>
            Сложность
            <select value={difficulty} onChange={event => setDifficulty(event.target.value)}>
                {Object.entries(difficultyLabels).map(([key, name]) => <option value={key} key={key}>{name}</option>)}
            </select>
        </label>}
        {mode === 'chatgpt' && <p className="muted small">ChatGPT играет чёрными через OpenAI API. Нужны интернет и API-ключ в application.yml. Запросы оплачиваются отдельно.</p>}
        {mode === 'network' && <p className="muted small">Вы играете белыми через localhost. Второй игрок открывает http://ВАШ_IP:8080 и получает чёрных. Каждый видит только свои карты. Адрес подключения выводится в консоль сервера.</p>}
        <label>
            Максимум карт в руке
            <input type="number" min="1" max="12" value={limit} onChange={event => setLimit(Number(event.target.value))}/>
        </label>
        <p className="muted small">Игроки начинают без карт. Добор и применение заменяют ход.</p>
        <button className="primary wide" disabled={busy || limit < 1 || limit > 12} onClick={() => void onStart()}>
            Начать партию
        </button>
    </>;
}
