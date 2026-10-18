import type { Theme } from '../hooks/useTheme';

type Props = { theme: Theme; onChange: (theme: Theme) => void };
export function ThemeSwitcher({ theme, onChange }: Props) {
    return <div className="theme-switcher" role="group" aria-label="Тема оформления">
        <button type="button" aria-label="Светлая тема" aria-pressed={theme === 'light'} onClick={() => onChange('light')}>
            <span aria-hidden="true">☀</span><span className="theme-name">Светлая</span>
        </button>
        <button type="button" aria-label="Тёмная тема" aria-pressed={theme === 'dark'} onClick={() => onChange('dark')}>
            <span aria-hidden="true">☾</span><span className="theme-name">Тёмная</span>
        </button>
    </div>;
}
