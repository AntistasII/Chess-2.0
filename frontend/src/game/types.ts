export type CardType = 'PAWN_BOOST' | 'KING_BOOST' | 'REVIVE_PAWN' | 'REVIVE_PIECE' | 'CONVERT' | 'FREEZE' | 'ASTRAL' | 'SHIELD' | 'CLEANSE' | 'SWAP' | 'SCOUT' | 'EXCHANGE';
export type Card = {
    id: number;
    type: CardType;
    name: string;
};
export type Piece = {
    id: number;
    square: number;
    type: string;
    white: boolean;
    boost: boolean;
    shield: boolean;
    frozen: boolean;
    astral: boolean;
    astralTurns?: number;
};
export type Action = {
    id: string;
    kind: 'move' | 'draw' | 'card';
    from: number;
    to: number;
    other: number;
    cardId: number;
    option: string;
};
export type BoardSize = {
    width: number;
    height: number;
    originFile: number;
    originRank: number;
};
export type State = {
    board: Piece[];
    boardSize: BoardSize;
    whiteTurn: boolean;
    mode: string;
    difficulty: string;
    handLimit: number;
    version: number;
    ply: number;
    hand: Card[];
    handWhite: boolean;
    enemyCards: number;
    deckCount: number;
    discardCount: number;
    check: boolean;
    result: string;
    canClaimDraw: boolean;
    actions: Action[];
    history: string[];
    captured: {
        type: string;
        white: boolean;
    }[];
    scouting: Card[];
};
