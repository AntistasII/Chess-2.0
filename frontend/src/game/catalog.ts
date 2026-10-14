import type { Card, CardType, State } from './types';
export const descriptions: Record<CardType, string> = {
    PAWN_BOOST: 'Вперёд на 2 клетки, первым ходом — на 3. Взятие обычное. До любого хода пешки.',
    KING_BOOST: 'До 2 клеток в одном направлении через свободную безопасную клетку. До любого хода короля.',
    REVIVE_PAWN: 'Возвращает взятую пешку на свободную клетку своего стартового пешечного ряда.',
    REVIVE_PIECE: 'Возвращает взятую фигуру, кроме пешки и короля, на свободную стартовую клетку её типа.',
    CONVERT: 'Вражеская пешка становится вашей и меняет направление движения.',
    FREEZE: 'Вражеская фигура не ходит в следующий ход владельца, но продолжает атаковать. Кроме короля.',
    ASTRAL: 'Убирает вражескую фигуру до конца второго хода владельца. Клетка зарезервирована. Кроме короля.',
    SHIELD: 'Защищает свою фигуру от всех вражеских карт до любого её хода. Обычное взятие разрешено.',
    CLEANSE: 'Снимает все эффекты с фигуры на доске. В астрале фигура недоступна.',
    SWAP: 'Меняет местами две соседние свои фигуры, кроме короля. Их щиты и усиления расходуются.',
    SCOUT: 'Посмотрите 2 верхние карты общей колоды и выберите их порядок. Применение нельзя отменить.',
    EXCHANGE: 'Выберите другую карту из своей руки и обменяйте её на случайную карту противника вслепую.',
};
export const names: Record<CardType, string> = {
    PAWN_BOOST: 'Усиление пешки',
    KING_BOOST: 'Усиление короля',
    REVIVE_PAWN: 'Возрождение пешки',
    REVIVE_PIECE: 'Возрождение фигуры',
    CONVERT: 'Смена стороны',
    FREEZE: 'Заморозка',
    ASTRAL: 'Астрал',
    SHIELD: 'Щит',
    CLEANSE: 'Очищение',
    SWAP: 'Перестановка',
    SCOUT: 'Разведка',
    EXCHANGE: 'Обмен планами'
};
export const difficultyLabels: Record<string, string> = { easy: 'Лёгкая', normal: 'Обычная', hard: 'Сложная' };
export const pieceNames: Record<string, string> = { P: 'Пешка', N: 'Конь', B: 'Слон', R: 'Ладья', Q: 'Ферзь', K: 'Король' };
export const resultText: Record<string, string> = {
    white: 'Белые победили — мат',
    black: 'Чёрные победили — мат',
    stalemate: 'Ничья — нет допустимых действий',
    repetition: 'Ничья — повторение позиции',
    seventyFiveMoves: 'Ничья — 75 ходов',
    claimedDraw: 'Ничья по заявлению'
};

export function gameStatus(game: State | null, busy: boolean): string {
    if (!game) return '';
    if (game.result) return resultText[game.result];
    if (busy && game.mode === 'chatgpt' && !game.whiteTurn) return 'ChatGPT обдумывает ход…';
    if (busy && game.mode === 'ai' && !game.whiteTurn) return 'ИИ обдумывает ход…';
    if (game.check) return 'Шах — нужно спасти короля';
    return `${game.whiteTurn ? 'Белые' : 'Чёрные'} ходят`;
}

export function selectionHint(card: Card | undefined, firstTarget: number | null, selected: number | null): string {
    if (!card) {
        return selected !== null
            ? 'Выберите подсвеченную клетку для хода.'
            : 'Сходите фигурой, возьмите карту или примените сохранённую.';
    }
    if (card.type === 'EXCHANGE') return 'Выберите другую свою карту для обмена.';
    if (card.type === 'SCOUT') return 'Нажмите «Применить» и выберите порядок двух карт.';
    if (card.type === 'SWAP' && firstTarget !== null) return 'Выберите вторую подсвеченную фигуру.';
    return 'Выберите подсвеченную клетку на доске.';
}
