import { afterEach, describe, expect, it, vi } from "vitest";
import {
  FLASH_DELAY_MS,
  FLASH_MS,
  MIN_OVERLAY_MS,
  clearReveal,
  completePublish,
  dismissFlash,
  failPublish,
  finishPublish,
  getPublishState,
  resetPublishFlow,
  setPublishStage,
  startPublish
} from "./publish-flow";

afterEach(() => {
  vi.useRealTimers();
  resetPublishFlow();
});

describe("Paylaşım akışı durumu", () => {
  it("başlar, aşamaları gerçek sırayla izler ve ikinci paylaşımı engeller", () => {
    expect(getPublishState().phase).toBe("idle");
    expect(startPublish()).toBe(true);
    expect(getPublishState()).toMatchObject({ phase: "publishing", stage: { kind: "post" } });
    expect(startPublish()).toBe(false);
    setPublishStage({ kind: "image", current: 2, total: 3 });
    expect(getPublishState().stage).toEqual({ kind: "image", current: 2, total: 3 });
  });

  it("backend onayından sonra liste beklenir; katman en az MIN_OVERLAY_MS açık kalır, sonra bildirim ve giriş animasyonu başlar", () => {
    vi.useFakeTimers();
    vi.setSystemTime(10_000);
    startPublish();
    completePublish("post-1");
    expect(getPublishState()).toMatchObject({ phase: "awaitingList", stage: { kind: "list" }, postId: "post-1", flash: null });
    finishPublish();
    // Minimum görünme süresi dolmadan katman kalkmaz.
    expect(getPublishState().phase).toBe("awaitingList");
    vi.advanceTimersByTime(MIN_OVERLAY_MS);
    expect(getPublishState()).toMatchObject({ phase: "idle", stage: null, revealId: "post-1", flash: null });
    vi.advanceTimersByTime(FLASH_DELAY_MS);
    expect(getPublishState().flash).toEqual({ postId: "post-1", at: 10_000 + MIN_OVERLAY_MS + FLASH_DELAY_MS });
    clearReveal();
    expect(getPublishState().revealId).toBeNull();
    expect(getPublishState().flash).not.toBeNull();
  });

  it("istek minimum süreden uzun sürerse bitişte ek gecikme olmaz", () => {
    vi.useFakeTimers();
    vi.setSystemTime(0);
    startPublish();
    vi.advanceTimersByTime(MIN_OVERLAY_MS + 2500);
    completePublish("slow");
    finishPublish();
    expect(getPublishState()).toMatchObject({ phase: "idle", revealId: "slow" });
  });

  it("başarı bildirimi görünür olduktan FLASH_MS + geçiş sonra depodan da silinir (kalıcı kalmaz)", () => {
    vi.useFakeTimers();
    vi.setSystemTime(0);
    startPublish();
    completePublish("p");
    finishPublish();
    vi.advanceTimersByTime(MIN_OVERLAY_MS + FLASH_DELAY_MS);
    expect(getPublishState().flash).not.toBeNull();
    vi.advanceTimersByTime(FLASH_MS);
    expect(getPublishState().flash).not.toBeNull();
    vi.advanceTimersByTime(1000);
    expect(getPublishState().flash).toBeNull();
  });

  it("hata ya da kısmi başarısızlıkta katman minimum süreden sonra kalkar ve başarı bildirimi kurulmaz", () => {
    vi.useFakeTimers();
    vi.setSystemTime(0);
    startPublish();
    failPublish();
    expect(getPublishState().phase).toBe("publishing");
    vi.advanceTimersByTime(MIN_OVERLAY_MS);
    expect(getPublishState()).toMatchObject({ phase: "idle", flash: null, revealId: null });
    expect(startPublish()).toBe(true);
    failPublish();
    finishPublish();
    vi.advanceTimersByTime(MIN_OVERLAY_MS + FLASH_DELAY_MS);
    expect(getPublishState().flash).toBeNull();
  });

  it("liste beklenmiyorken finish etkisizdir (bildirim uydurulmaz)", () => {
    finishPublish();
    expect(getPublishState().flash).toBeNull();
    startPublish();
    finishPublish();
    expect(getPublishState().phase).toBe("publishing");
  });

  it("her yeni başarılı paylaşım yeni 'at' üretir; yalnız eşleşen 'at' bildirimi kapatır", () => {
    vi.useFakeTimers();
    vi.setSystemTime(1000);
    startPublish();
    completePublish("a");
    finishPublish();
    vi.advanceTimersByTime(MIN_OVERLAY_MS + FLASH_DELAY_MS);
    const first = getPublishState().flash!.at;
    vi.setSystemTime(first + 500);
    startPublish();
    completePublish("b");
    finishPublish();
    vi.advanceTimersByTime(MIN_OVERLAY_MS + FLASH_DELAY_MS);
    const second = getPublishState().flash!.at;
    expect(second).toBeGreaterThan(first);
    dismissFlash(first);
    expect(getPublishState().flash?.postId).toBe("b");
    dismissFlash(second);
    expect(getPublishState().flash).toBeNull();
  });
});
