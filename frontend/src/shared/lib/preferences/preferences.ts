/**
 * Hassas olmayan arayüz tercihleri (tema, kenar çubuğu, navbar davranışı). Token veya kullanıcı verisi içermez.
 * Doğruluk kaynağı <html> attribute'larıdır; ilk boyamadan önce init-script ayarlar,
 * böylece FOUC ve hydration kayması olmaz.
 */
export const THEME_STORAGE_KEY = "ltz-theme";
export const SIDEBAR_STORAGE_KEY = "ltz-sidebar";
export const NAVBAR_STORAGE_KEY = "ltz-navbar";
export const TRANSITION_STORAGE_KEY = "ltz-transition";
export const PREFERENCES_EVENT = "ltz:preferences";

export const themePreferences = ["system", "light", "dark"] as const;
export type ThemePreference = (typeof themePreferences)[number];
export type ResolvedTheme = "light" | "dark";

/** `auto`: aşağı kaydırınca gizlenir, yukarı kaydırınca döner (varsayılan). `fixed`: hep görünür. */
export const navbarModes = ["auto", "fixed"] as const;
export type NavbarMode = (typeof navbarModes)[number];

/**
 * Tema geçişi paketi. `v3`: LTZ taktik operasyon sahneleri (varsayılan), `v2`: önceki taktik geçiş (legacy),
 * `v1`: klasik gün doğumu / fırtınalı gece, `minimal`: animasyonsuz anlık geçiş. Tema seçiminden bağımsızdır.
 * Tanınmayan saklı değerler güvenle varsayılana düşer.
 */
export const transitionPacks = ["v3", "v2", "v1", "minimal"] as const;
export type TransitionPack = (typeof transitionPacks)[number];
export const defaultTransitionPack: TransitionPack = "v3";

export function parseTransitionPack(value: unknown): TransitionPack {
  return (transitionPacks as readonly unknown[]).includes(value) ? (value as TransitionPack) : defaultTransitionPack;
}

export function parseNavbarMode(value: unknown): NavbarMode {
  return value === "fixed" ? "fixed" : "auto";
}

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

/** Tercihi (data-theme-pref + depolama) yazar; görünen temayı değiştirmez. */
export function storeThemePreference(preference: ThemePreference): void {
  document.documentElement.dataset.themePref = preference;
  writeStored(THEME_STORAGE_KEY, preference === "system" ? null : preference);
  notifyPreferencesChanged();
}

/** Mevcut tercihe göre görünen temayı (data-theme) belirler. */
export function applyResolvedTheme(preference: ThemePreference): ResolvedTheme {
  const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
  const resolved = resolveTheme(preference, prefersDark);
  document.documentElement.dataset.theme = resolved;
  notifyPreferencesChanged();
  return resolved;
}

/** Görünen tema, verilen tercih için ne olurdu (DOM'a dokunmadan). */
export function previewResolvedTheme(preference: ThemePreference): ResolvedTheme {
  const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
  return resolveTheme(preference, prefersDark);
}

export function applyThemePreference(preference: ThemePreference): void {
  storeThemePreference(preference);
  applyResolvedTheme(preference);
}

export function applyNavbarMode(mode: NavbarMode): void {
  const root = document.documentElement;
  if (mode === "fixed") {
    root.dataset.navbar = "fixed";
  } else {
    delete root.dataset.navbar;
  }
  writeStored(NAVBAR_STORAGE_KEY, mode === "fixed" ? "fixed" : null);
  notifyPreferencesChanged();
}

/** Varsayılan paket <html> üzerinde nitelik taşımaz; seçili diğer paketler `data-transition` yazar. */
export function applyTransitionPack(pack: TransitionPack): void {
  const root = document.documentElement;
  if (pack === defaultTransitionPack) {
    delete root.dataset.transition;
  } else {
    root.dataset.transition = pack;
  }
  writeStored(TRANSITION_STORAGE_KEY, pack === defaultTransitionPack ? null : pack);
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
