import { useEffect, useRef, useState } from 'react';

type Props = {
    busy: boolean;
    guest: boolean;
    onNew: () => void;
    onContinue: () => void;
    onSave: () => void;
    onDownload: () => void;
    onUpload: (file: File) => void;
    onRules: () => void;
    onReset: () => void;
};

export function GameMenu(props: Props) {
    const [open, setOpen] = useState(false);
    const root = useRef<HTMLDivElement>(null);
    const toggle = useRef<HTMLButtonElement>(null);
    const upload = useRef<HTMLInputElement>(null);
    useEffect(() => {
        if (!open) return;
        root.current?.querySelector<HTMLButtonElement>('[role="menuitem"]:not(:disabled)')?.focus();
        const outside = (event: PointerEvent) => {
            if (!root.current?.contains(event.target as Node)) setOpen(false);
        };
        document.addEventListener('pointerdown', outside);
        return () => document.removeEventListener('pointerdown', outside);
    }, [open]);
    const choose = (action: () => void) => {
        setOpen(false); toggle.current?.focus(); action();
    };
    return <div className="game-menu" ref={root} onBlur={event => {
        if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false);
    }} onKeyDown={event => {
        if (event.key === 'Escape') { setOpen(false); toggle.current?.focus(); }
        if (!open || !['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
        event.preventDefault();
        const items = Array.from(root.current!.querySelectorAll<HTMLButtonElement>('[role="menuitem"]:not(:disabled)'));
        const index = items.indexOf(document.activeElement as HTMLButtonElement);
        const next = event.key === 'Home' ? 0 : event.key === 'End' ? items.length - 1
            : (index + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
        items[next]?.focus();
    }}>
        <button ref={toggle} className="menu-toggle" aria-label="Меню игры" aria-haspopup="menu" aria-expanded={open}
            aria-controls="game-menu" onClick={() => setOpen(!open)}>
            <span className="hamburger" aria-hidden="true"><i/><i/><i/></span><span>Меню</span>
        </button>
        <input ref={upload} type="file" accept=".json,application/json" hidden onChange={event => {
            const file = event.target.files?.[0]; event.target.value = '';
            if (file && window.confirm('Загрузить партию из файла? Текущая позиция будет заменена.')) props.onUpload(file);
        }}/>
        {open && <div id="game-menu" className="menu-popover" role="menu" aria-label="Действия с партией">
            <button role="menuitem" disabled={props.busy || props.guest} onClick={() => choose(props.onNew)}>Начать новую</button>
            <button role="menuitem" disabled={props.busy || props.guest} onClick={() => choose(props.onContinue)}>Продолжить</button>
            <button role="menuitem" disabled={props.busy || props.guest} onClick={() => choose(props.onSave)}>Сохранить</button>
            <button role="menuitem" disabled={props.busy || props.guest} onClick={() => choose(props.onDownload)}>Сохранить в файл</button>
            <button role="menuitem" disabled={props.busy || props.guest} onClick={() => choose(() => upload.current?.click())}>Загрузить из файла</button>
            <button role="menuitem" onClick={() => choose(props.onRules)}>Правила</button>
            <button role="menuitem" className="menu-reset" disabled={props.busy || props.guest} onClick={() => choose(props.onReset)}>Перезапустить полностью</button>
            {props.guest && <small>Управление партией доступно владельцу за белых.</small>}
        </div>}
    </div>;
}
