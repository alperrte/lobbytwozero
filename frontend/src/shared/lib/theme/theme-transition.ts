import {
  applyResolvedTheme,
  previewResolvedTheme,
  storeThemePreference,
  type ResolvedTheme,
  type ThemePreference
} from "@/shared/lib/preferences/preferences";

/**
 * Tema geçişi sahneleri.
 * - sunrise: Dark → Light ("Gün doğumu")
 * - storm: Light → Dark ("Fırtınalı gece")
 * Sahne yalnız bir örtü katmanıdır (pointer-events: none); gerçek tema, örtü ekranı
 * kapladığı anda (APPLY_AT_MS) değişir ve örtü TOTAL_MS sonunda DOM'dan kaldırılır.
 */
export type ThemeScene = "sunrise" | "storm";
export type ThemeTransitionState = { id: number; scene: ThemeScene } | null;

export const TRANSITION_TOTAL_MS = 1400;
export const TRANSITION_APPLY_AT_MS = 560;

let state: ThemeTransitionState = null;
let counter = 0;
let applyTimer: ReturnType<typeof setTimeout> | undefined;
let endTimer: ReturnType<typeof setTimeout> | undefined;
let pendingPreference: ThemePreference | undefined;
const listeners = new Set<() => void>();

function emit(next: ThemeTransitionState) {
  state = next;
  listeners.forEach((listener) => listener());
}

function clearTimers() {
  clearTimeout(applyTimer);
  clearTimeout(endTimer);
  applyTimer = undefined;
  endTimer = undefined;
  pendingPreference = undefined;
}

export function getThemeTransition(): ThemeTransitionState {
  return state;
}

export function subscribeThemeTransition(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/**
 * Süren bir geçişi hemen bitirir (örtü kaldırılırken, ör. rota grubu değişince).
 * Tema henüz uygulanmadıysa seçilen tercih şimdi uygulanır; tercih asla yarım kalmaz.
 */
export function finishThemeTransition(): void {
  const waiting = pendingPreference;
  clearTimers();
  if (waiting !== undefined) {
    applyResolvedTheme(waiting);
  }
  if (state !== null) {
    emit(null);
  }
}

function prefersReducedMotion(): boolean {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

function currentTheme(): ResolvedTheme {
  return document.documentElement.dataset.theme === "light" ? "light" : "dark";
}

/**
 * Tema tercihini uygular. Görünen tema değişecekse ve hareket azaltma istenmiyorsa
 * sahne oynatılır; aksi halde tema anında değişir. Art arda çağrılar güvenlidir:
 * önceki zamanlayıcılar iptal edilir, son istek kazanır.
 */
export function changeTheme(preference: ThemePreference): void {
  clearTimers();
  storeThemePreference(preference);

  const target = previewResolvedTheme(preference);
  if (target === currentTheme() || prefersReducedMotion()) {
    applyResolvedTheme(preference);
    if (state !== null) {
      emit(null);
    }
    return;
  }

  counter += 1;
  emit({ id: counter, scene: target === "light" ? "sunrise" : "storm" });

  pendingPreference = preference;
  applyTimer = setTimeout(() => {
    pendingPreference = undefined;
    applyResolvedTheme(preference);
  }, TRANSITION_APPLY_AT_MS);
  endTimer = setTimeout(() => {
    emit(null);
  }, TRANSITION_TOTAL_MS);
}
