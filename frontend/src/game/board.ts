import type { BoardSize } from './types';

export const standardBoard: BoardSize = { width: 8, height: 8, originFile: 0, originRank: 0 };

// Подписи вертикалей поддерживают размеры за пределами стандартных a…h.
export function fileName(file: number): string {
    let value = file + 1;
    let name = '';
    while (value > 0) {
        value--;
        name = String.fromCharCode(97 + value % 26) + name;
        value = Math.floor(value / 26);
    }
    return name;
}

export function squareName(square: number, width: number): string {
    return `${fileName(square % width)}${Math.floor(square / width) + 1}`;
}

export function boardOrder(size: BoardSize, flipped: boolean): number[] {
    const { width, height } = size;
    return Array.from({ length: width * height }, (_, i) => flipped
        ? Math.floor(i / width) * width + width - 1 - i % width
        : (height - 1 - Math.floor(i / width)) * width + i % width);
}
