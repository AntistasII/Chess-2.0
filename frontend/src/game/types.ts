export type CardType = 'PAWN_BOOST' | 'KING_BOOST' | 'REVIVE_PAWN' | 'REVIVE_PIECE' | 'CONVERT' | 'FREEZE' | 'ASTRAL' | 'SHIELD' | 'CLEANSE' | 'SWAP' | 'SCOUT' | 'EXCHANGE' | 'UNDO_MOVE' | 'EXPAND_BOTTOM' | 'EXPAND_TOP' | 'EXPAND_LEFT' | 'EXPAND_RIGHT' | 'DESTROY_CARD' | 'SACRIFICE' | 'TRAP' | 'HIDDEN_TRAP' | 'WHITE_HOLE';
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
    frozenTurns?: number;
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
export type SquareEffect = {
    square: number;
    type: 'TRAP' | 'HIDDEN_TRAP' | 'WHITE_HOLE';
    white: boolean;
    turns: number;
    freezeTurns: number;
};
export type State = {
    squareEffects?: SquareEffect[];
    castlingRights?: { white: [boolean, boolean]; black: [boolean, boolean] };
    opponentRetryLimit: number;
    defaultMode: string;
    networkOnly?: boolean;
    openAiConfigured?: boolean;
    apiKeyInputSupported?: boolean;
    apiKeyEntry?: boolean;
    newGameDefaults: Record<string, { handLimit: number; difficulty: string }>;
    board: Piece[];
    boardSize: BoardSize;
    whiteTurn: boolean;
    playerWhite?: boolean;
    mode: string;
    difficulty: string;
    handLimit: number;
    version: number;
    gameId?: string;
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
