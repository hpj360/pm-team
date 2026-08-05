import { create } from 'zustand';

type ThemeMode = 'light' | 'dark';

interface ThemeState {
  mode: ThemeMode;
  toggleTheme: () => void;
  setTheme: (mode: ThemeMode) => void;
}

const THEME_STORAGE_KEY = 'app-theme';

export const useThemeStore = create<ThemeState>((set, get) => ({
  mode: (() => {
    try {
      const saved = localStorage.getItem(THEME_STORAGE_KEY);
      return saved === 'dark' ? 'dark' : 'light';
    } catch {
      return 'light';
    }
  })(),
  toggleTheme: () => {
    const next = get().mode === 'light' ? 'dark' : 'light';
    set({ mode: next });
    try { localStorage.setItem(THEME_STORAGE_KEY, next); } catch {}
  },
  setTheme: (mode) => {
    set({ mode });
    try { localStorage.setItem(THEME_STORAGE_KEY, mode); } catch {}
  },
}));
