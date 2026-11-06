/**
 * Hassas olmayan arayüz tercihleri (tema, kenar çubuğu). Token veya kullanıcı verisi içermez.
 * Doğruluk kaynağı <html> attribute'larıdır; ilk boyamadan önce init-script ayarlar,
 * böylece FOUC ve hydration kayması olmaz.
 */
export const THEME_STORAGE_KEY = "ltz-theme";
export const SIDEBAR_STORAGE_KEY = "ltz-sidebar";
export const PREFERENCES_EVENT = "ltz:preferences";

export const themePreferences = ["system", "light", "dark"] as const;
export type ThemePreference = (typeof themePreferences)[number];
export type ResolvedTheme = "light" | "dark";

export function parseThemePreference(value: unknown): ThemePreference {
  return value === "light" || value === "dark" ? value : "system";
}

export function resolveTheme(
  preference: ThemePreference,
  prefersDark: boolean
): ResolvedTheme {
  if (preference === "system") {
    return prefersDark ? "dark" : "light";
  }
  return preference;
}

export function readStored(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function writeStored(key: string, value: string | null): void {
  try {
    if (value === null) {
      window.localStorage.removeItem(key);
    } else {
      window.localStorage.setItem(key, value);
    }
  } catch {
    // Depolama kapalıysa tercih yalnız oturum boyunca <html> üzerinde kalır.
  }
}

export function notifyPreferencesChanged(): void {
  window.dispatchEvent(new Event(PREFERENCES_EVENT));
}

export function subscribePreferences(onChange: () => void): () => void {
  const media = window.matchMedia("(prefers-color-scheme: dark)");
  const onMedia = () => {
    const root = document.documentElement;
    if (parseThemePreference(root.dataset.themePref) === "system") {
      root.dataset.theme = resolveTheme("system", media.matches);
    }
    onChange();
  };

  window.addEventListener(PREFERENCES_EVENT, onChange);
  window.addEventListener("storage", onChange);
  media.addEventListener("change", onMedia);
  return () => {
    window.removeEventListener(PREFERENCES_EVENT, onChange);
    window.removeEventListener("storage", onChange);
    media.removeEventListener("change", onMedia);
  };
}

export function applyThemePreference(preference: ThemePreference): void {
  const root = document.documentElement;
  const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
  root.dataset.themePref = preference;
  root.dataset.theme = resolveTheme(preference, prefersDark);
  writeStored(THEME_STORAGE_KEY, preference === "system" ? null : preference);
  notifyPreferencesChanged();
}

export function applySidebarCollapsed(collapsed: boolean): void {
  const root = document.documentElement;
  if (collapsed) {
    root.dataset.sidebar = "collapsed";
  } else {
    delete root.dataset.sidebar;
  }
  writeStored(SIDEBAR_STORAGE_KEY, collapsed ? "collapsed" : null);
  notifyPreferencesChanged();
}
