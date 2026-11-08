import { useSyncExternalStore } from "react";

/**
 * Paylaşım akışının bellek içi durumu (depoya yazılmaz). Tam ekran yükleme katmanı Community yerleşiminde durduğu için
 * oluşturma sayfasından akışa geçişte kaybolmaz; katman ancak yeni gönderi listede görünmeye hazır olunca kalkar.
 *
 *   idle → publishing (istek/yüklemeler sürerken) → awaitingList (backend onayladı, liste yenileniyor) → idle
 *
 * Başarı yalnız backend onayından (`completePublish`) sonra bildirilir; hata ya da kısmi başarısızlıkta `failPublish`
 * katmanı kaldırır ve başarı bildirimi hiç kurulmaz.
 *
 * Katman çok hızlı yanıp sönmesin diye en az `MIN_OVERLAY_MS` görünür. Bu yalnız katmanın KAPANIŞINI geciktirir: gerçek
 * istekler ve liste yenilemesi hiç bekletilmez; istek daha uzun sürerse katman tamamlanana dek açık kalır.
 */
export type PublishStage = { kind: "post" } | { kind: "image"; current: number; total: number } | { kind: "list" };

export type PublishFlash = { postId: string; at: number };

export type PublishState = {
  phase: "idle" | "publishing" | "awaitingList";
  stage: PublishStage | null;
  postId: string | null;
  /** Başarı bildirimi (2 sn görünür); her yeni başarılı paylaşım yeni `at` ile süreyi yeniden başlatır. */
  flash: PublishFlash | null;
  /** Listede giriş animasyonuyla belirecek yeni gönderi. */
  revealId: string | null;
};

const IDLE: PublishState = { phase: "idle", stage: null, postId: null, flash: null, revealId: null };

/** Katmanın en az görünme süresi (ms). */
export const MIN_OVERLAY_MS = 1200;
/** Başarı bildirimi bu kadar görünür kalır, ardından `FADE_MS` süren geçişle kaybolur. */
export const FLASH_MS = 2000;
export const FADE_MS = 300;
/** Bildirim, yeni gönderinin giriş animasyonundan ("ardından") sonra başlar; hiçbir şeyi bekletmez. */
export const FLASH_DELAY_MS = 520;

let state: PublishState = IDLE;
let flashTimer: ReturnType<typeof setTimeout> | undefined;
let expiryTimer: ReturnType<typeof setTimeout> | undefined;
let settleTimer: ReturnType<typeof setTimeout> | undefined;
let startedAt = 0;
const listeners = new Set<() => void>();

function set(next: Partial<PublishState>): void {
  state = { ...state, ...next };
  listeners.forEach((listener) => listener());
}

export const getPublishState = (): PublishState => state;
export const subscribePublish = (listener: () => void): (() => void) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

/** Paylaşımı başlatır; zaten sürüyorsa `false` döner (tekrar paylaşım engellenir). */
export function startPublish(): boolean {
  if (state.phase !== "idle") return false;
  startedAt = Date.now();
  set({ phase: "publishing", stage: { kind: "post" }, postId: null, revealId: null });
  return true;
}

export function setPublishStage(stage: PublishStage): void {
  if (state.phase === "publishing") set({ stage });
}

/** Katman kapanışını, minimum görünme süresi dolana dek (gerekirse) erteler; gerçek isteklere dokunmaz. */
function settle(apply: () => void): void {
  const wait = Math.max(0, MIN_OVERLAY_MS - (Date.now() - startedAt));
  if (settleTimer) clearTimeout(settleTimer);
  if (wait === 0) {
    settleTimer = undefined;
    apply();
    return;
  }
  settleTimer = setTimeout(() => {
    settleTimer = undefined;
    apply();
  }, wait);
}

/** İstek başarısız ya da kısmen başarısız: katman kalkar, başarı bildirimi kurulmaz. */
export function failPublish(): void {
  if (state.phase === "idle") return;
  settle(() => set({ phase: "idle", stage: null, postId: null }));
}

/** Backend gönderiyi onayladı; liste yenilenene dek katman sürer. */
export function completePublish(postId: string): void {
  set({ phase: "awaitingList", stage: { kind: "list" }, postId });
}

/** Liste yeni gönderiyi göstermeye hazır: katman kalkar, giriş animasyonu ve başarı bildirimi başlar. */
export function finishPublish(): void {
  if (state.phase !== "awaitingList" || !state.postId) return;
  const postId = state.postId;
  settle(() => {
    if (state.phase !== "awaitingList") return;
    set({ phase: "idle", stage: null, revealId: postId, postId: null });
    // Giriş animasyonu bitince bildirim görünür (süresi görünür olduğu andan başlar) ve süresi dolunca depodan da silinir:
    // bildirim hiçbir durumda (ör. başka sayfaya gidip dönünce) kalıcı kalmaz.
    if (flashTimer) clearTimeout(flashTimer);
    if (expiryTimer) clearTimeout(expiryTimer);
    flashTimer = setTimeout(() => {
      const at = Date.now();
      set({ flash: { postId, at } });
      expiryTimer = setTimeout(() => dismissFlash(at), FLASH_MS + FADE_MS + 400);
    }, FLASH_DELAY_MS);
  });
}

export function dismissFlash(at: number): void {
  if (state.flash?.at === at) set({ flash: null });
}

export function clearReveal(): void {
  if (state.revealId !== null) set({ revealId: null });
}

/** Testler için. */
export function resetPublishFlow(): void {
  [flashTimer, expiryTimer, settleTimer].forEach((timer) => timer && clearTimeout(timer));
  flashTimer = expiryTimer = settleTimer = undefined;
  startedAt = 0;
  state = IDLE;
  listeners.forEach((listener) => listener());
}

export function usePublishState(): PublishState {
  return useSyncExternalStore(subscribePublish, getPublishState, () => IDLE);
}
