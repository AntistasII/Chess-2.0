import type { ReactNode } from 'react';
import type { CardType } from '../game/types';

type IconType = CardType | 'deck' | 'settings' | 'book';

function shape(type: IconType): ReactNode {
    switch (type) {
        case 'UNDO_MOVE':
            return <path d="M9 16h17a13 13 0 1 1-11 20M9 16l9-9M9 16l9 9"/>;
        case 'EXPAND_TOP':
        case 'EXPAND_BOTTOM':
        case 'EXPAND_LEFT':
        case 'EXPAND_RIGHT':
            return <><rect x="7" y="7" width="34" height="34" rx="2"/><path d="M24 13v22M13 24h22"/></>;
        case 'DESTROY_CARD':
            return <><rect x="12" y="6" width="25" height="36" rx="3"/><path d="m17 16 15 16m0-16L17 32"/></>;
        case 'SACRIFICE':
            return <path d="M9 39h30M14 32l10-22 10 22H14M18 20h12M24 5v5"/>;
        case 'TRAP':
        case 'HIDDEN_TRAP':
            return <><circle cx="24" cy="26" r="15"/><path d="m12 17 8 9-7 8m23-17-8 9 7 8M24 11v8m0 15v7"/></>;
        case 'WHITE_HOLE':
            return <><circle cx="24" cy="24" r="9"/><path d="M24 3v7m0 28v7M3 24h7m28 0h7M9 9l5 5m20 20 5 5M9 39l5-5m20-20 5-5"/></>;
        case 'SHIELD':
            return <path d="M24 5 40 11v12c0 9-8 16-16 20C16 39 8 32 8 23V11zM24 6v35"/>;
        case 'FREEZE':
            return <path d="M24 5v38M8 14l32 20M8 34l32-20m-21-5 5 5 5-5m-10 30 5-5 5 5M8 20l7-2-1-7m20 26-1-7 7-2M8 28l7 2-1 7m20-26-1 7 7 2"/>;
        case 'SCOUT':
            return <>
                <rect x="6" y="7" width="24" height="34" rx="3"/>
                <path d="M34 8h5v31h-5M9 24s6-8 12-8 12 8 12 8-6 8-12 8S9 24 9 24z"/>
                <circle cx="21" cy="24" r="3"/>
            </>;
        case 'EXCHANGE':
        case 'SWAP':
        case 'CONVERT':
            return <>
                <path d="M6 16h34l-6-6m6 22H6l6 6"/>
                <rect x="8" y="21" width="9" height="7" rx="2"/>
                <rect x="31" y="21" width="9" height="7" rx="2"/>
            </>;
        case 'CLEANSE':
            return <path d="m24 7 4 12 13 5-13 4-4 13-4-13-13-4 13-5zM39 7v7m-3-4h7"/>;
        case 'ASTRAL':
            return <>
                <path strokeDasharray="3 4" d="M24 7c8 8 10 14 4 19l6 13H14l6-13c-6-5-4-11 4-19z"/>
                <path d="M12 43h24"/>
            </>;
        case 'PAWN_BOOST':
        case 'KING_BOOST':
            return <path d="M15 39V10m-6 6 6-6 6 6M33 39V10m-6 6 6-6 6 6"/>;
        case 'REVIVE_PAWN':
        case 'REVIVE_PIECE':
            return <>
                <path d="M12 15a15 15 0 1 1-2 18M5 15h10V5"/>
                <path d="M19 32h11l-2-8h-7z"/>
                <circle cx="24.5" cy="20" r="4"/>
            </>;
        case 'book':
            return <path d="M24 12C17 6 9 7 5 9v28c6-3 13-2 19 3 6-5 13-6 19-3V9c-4-2-12-3-19 3zM24 12v28"/>;
        case 'settings':
            return <>
                <circle cx="24" cy="24" r="7"/>
                <path d="m20 6 8 0 2 7 7 2 5 6-4 6 0 7-7 3-3 5h-8l-3-5-7-3v-7l-4-6 5-6 7-2z"/>
            </>;
        case 'deck':
            return <>
                <rect x="12" y="8" width="27" height="35" rx="4"/>
                <path d="M8 37H6V5h27v1M25 18v15m-7-7h14"/>
            </>;
    }
}

export function Icon({ type }: { type: IconType }) {
    return <svg viewBox="0 0 48 48" fill="none" stroke="currentColor" strokeWidth="2"
        strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        {shape(type)}
    </svg>;
}
