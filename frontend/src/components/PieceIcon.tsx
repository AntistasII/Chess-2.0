export function PieceIcon({ type, white = true, ghost = false }: {
    type: string;
    white?: boolean;
    ghost?: boolean;
}) {
    const fill = white ? '#fffdf5' : '#27332e';
    return <svg className={`piece ${ghost ? 'ghost' : ''}`} viewBox="0 0 64 64" aria-hidden="true" fill={fill} stroke="#27332e" strokeWidth="2.1" strokeLinejoin="round" strokeLinecap="round">
        {type === 'P' && <>
            <circle cx="32" cy="18" r="8"/>
            <path d="M25 26h14l-3 8c0 8 8 10 8 17H20c0-7 8-9 8-17z"/>
        </>}
        {type === 'R' && <path d="M18 12h7v7h7v-7h7v7h7v-7h2v15l-7 5 2 19H21l2-19-7-5V12z"/>}
        {type === 'B' && <>
            <path d="M32 9c9 8 14 16 8 22l-5 5 6 15H23l6-15-5-5c-6-6-1-14 8-22z"/>
            <path d="m31 16 5 10" fill="none"/>
        </>}
        {type === 'Q' && <>
            <path d="m17 20 8 7 7-13 7 13 8-7-5 20H22zM24 40h16l4 11H20z"/>
            <circle cx="16" cy="18" r="3"/>
            <circle cx="32" cy="12" r="3"/>
            <circle cx="48" cy="18" r="3"/>
        </>}
        {type === 'K' && <>
            <path d="M32 9v12m-6-7h12" fill="none"/>
            <path d="M32 26c-8-12-19-3-15 6l8 8-3 11h20l-3-11 8-8c4-9-7-18-15-6z"/>
        </>}
        {type === 'N' && <>
            <path d="m25 11 4 7 10-4c9 6 9 17 5 25l-3 12H19c1-10 6-17 13-21l-9 2-9-7 11-12z"/>
            <circle cx="32" cy="22" r="1.6" fill="#27332e" stroke="none"/>
        </>}
        <path d="M18 51h28l3 5H15z"/>
    </svg>;
}
