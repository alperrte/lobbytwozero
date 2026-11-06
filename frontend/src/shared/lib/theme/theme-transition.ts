import {
  applyResolvedTheme,
  parseTransitionPack,
  previewResolvedTheme,
  storeThemePreference,
  type ResolvedTheme,
  type ThemePreference
} from "@/shared/lib/preferences/preferences";
import {
  resolveSceneTiming,
  sceneFor,
  V1_APPLY_AT_MS,
  V1_TOTAL_MS,
  type ThemeScene
} from "./transition-scenes";

/**
 * Tema geçişi denetleyicisi (paylaşılan durum makinesi). Sahneyi seçili pakete göre (`data-transition`:
 * işaretsiz = v3, v2 / v1 / minimal) kayıttan (transition-scenes.ts) seçer; sahne içeriğini bilmez.
 * Sahne yalnız bir örtü katmanıdır (pointer-events: none); gerçek tema, örtü ekranı
 * kapladığı anda (sahnenin applyAtMs değeri) değişir ve örtü totalMs sonunda DOM'dan kaldırılır.
 * Görünen tema ile animasyon durumu ayrıdır: animasyon takılsa bile tema her zaman uygulanır.
 */
export type { ThemeScene };
export type ThemeTransitionState = {
  id: number;
  scene: ThemeScene;
  totalMs: number;
  /** Süreler dar ekran çizelgesinden mi seçildi; sahne aynı çizelgeyi oynatır. */
  compact: boolean;
  /** Geçişin başladığı an (performance.now()); sahne animasyonlarını tema zamanlayıcısına hizalar. */
  startedAt: number;
} | null;

/** V1 (klasik) sahnelerinin süreleri; geriye uyumluluk için korunur. */
export const TRANSITION_TOTAL_MS = V1_TOTAL_MS;
export const TRANSITION_APPLY_AT_MS = V1_APPLY_AT_MS;

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

function isCompactViewport(): boolean {
  return window.matchMedia("(max-width: 767px)").matches;
}

/**
 * Tema tercihini uygular. Görünen tema değişecekse, seçili paket bir sahne tanımlıyorsa
 * (`minimal` tanımlamaz) ve hareket azaltma istenmiyorsa sahne oynatılır; aksi halde tema anında değişir.
 * Art arda çağrılar güvenlidir: önceki zamanlayıcılar iptal edilir, son istek kazanır.
 */
export function changeTheme(preference: ThemePreference): void {
  clearTimers();
  storeThemePreference(preference);

  const target = previewResolvedTheme(preference);
  const pack = parseTransitionPack(document.documentElement.dataset.transition);
  const scene = sceneFor(pack, target);
  if (target === currentTheme() || scene === null || prefersReducedMotion()) {
    applyResolvedTheme(preference);
    if (state !== null) {
      emit(null);
    }
    return;
  }

  const compact = isCompactViewport();
  const { totalMs, applyAtMs } = resolveSceneTiming(scene, compact);
  counter += 1;
  emit({ id: counter, scene, totalMs, compact, startedAt: performance.now() });

  pendingPreference = preference;
  applyTimer = setTimeout(() => {
    pendingPreference = undefined;
    applyResolvedTheme(preference);
  }, applyAtMs);
  endTimer = setTimeout(() => {
    emit(null);
  }, totalMs);
}
