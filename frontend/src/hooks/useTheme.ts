import { useEffect, useState } from 'react';

export type Theme = 'light' | 'dark';
const storageKey = 'chess-theme';

export function useTheme() {
    const [theme, setTheme] = useState<Theme>(() => document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light');
    useEffect(() => {
        document.documentElement.dataset.theme = theme;
        document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#141416' : '#f5f7f2');
        try { localStorage.setItem(storageKey, theme); } catch { /* Настройка работает и без хранилища. */ }
    }, [theme]);
    useEffect(() => {
        const sync = (event: StorageEvent) => {
            if (event.key === storageKey) setTheme(event.newValue === 'dark' ? 'dark' : 'light');
        };
        window.addEventListener('storage', sync);
        return () => window.removeEventListener('storage', sync);
    }, []);
    return { theme, setTheme };
}
