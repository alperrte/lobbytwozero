import { act, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import {
  MIN_OVERLAY_MS,
  completePublish,
  failPublish,
  finishPublish,
  getPublishState,
  resetPublishFlow,
  setPublishStage,
  startPublish
} from "../services/publish-flow";
import { PublishOverlay } from "./publish-overlay";

function mount() {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr}>
      <PublishOverlay />
    </NextIntlClientProvider>
  );
}
const dialog = () => document.querySelector("dialog") as HTMLDialogElement;

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(0);
});
afterEach(() => {
  act(() => resetPublishFlow());
  vi.useRealTimers();
  document.documentElement.style.overflow = "";
});

describe("PublishOverlay (tam ekran yükleme katmanı)", () => {
  it("işlem yokken kapalıdır ve içerik üretmez", () => {
    mount();
    expect(dialog().open).toBe(false);
    expect(screen.queryByText("Gönderin paylaşılıyor…")).toBeNull();
  });

  it("paylaşım başlayınca modal açılır; metin, aşama ve aria-busy gelir; arka plan kaydırması kilitlenir", () => {
    mount();
    act(() => {
      startPublish();
    });
    expect(dialog().open).toBe(true);
    expect(dialog()).toHaveAttribute("aria-busy", "true");
    expect(screen.getByText("Gönderin paylaşılıyor…")).toBeInTheDocument();
    expect(screen.getByText("Gönderi oluşturuluyor…")).toBeInTheDocument();
    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(document.documentElement.style.overflow).toBe("hidden");
    expect(dialog()).toHaveAccessibleName("Gönderin paylaşılıyor…");
  });

  it("gerçek aşamayı yazar (görsel sayacı, liste güncelleme); yüzde göstermez", () => {
    mount();
    act(() => {
      startPublish();
      setPublishStage({ kind: "image", current: 2, total: 3 });
    });
    expect(screen.getByText("Görsel 2/3 yükleniyor…")).toBeInTheDocument();
    act(() => completePublish("p1"));
    expect(screen.getByText("Akış güncelleniyor…")).toBeInTheDocument();
    expect(dialog().textContent).not.toMatch(/%/);
  });

  it("backend onayından sonra liste hazır olana dek açık kalır; en az 1,2 sn görünür, sonra kapanır ve kaydırma geri açılır", () => {
    mount();
    act(() => {
      startPublish();
      completePublish("p1");
    });
    expect(dialog().open).toBe(true);
    act(() => finishPublish());
    // İstek anında bitse de katman minimum süre dolmadan kapanmaz.
    expect(dialog().open).toBe(true);
    act(() => void vi.advanceTimersByTime(MIN_OVERLAY_MS - 50));
    expect(dialog().open).toBe(true);
    act(() => void vi.advanceTimersByTime(60));
    expect(dialog().open).toBe(false);
    expect(document.documentElement.style.overflow).toBe("");
    expect(getPublishState().revealId).toBe("p1");
  });

  it("istek minimum süreden uzun sürerse bitince hemen kapanır (ek gecikme yok)", () => {
    mount();
    act(() => {
      startPublish();
      completePublish("p1");
    });
    act(() => void vi.advanceTimersByTime(MIN_OVERLAY_MS + 3000));
    expect(dialog().open).toBe(true);
    act(() => finishPublish());
    expect(dialog().open).toBe(false);
  });

  it("hata durumunda katman minimum süreden sonra kapanır ve başarı bildirimi kurulmaz", () => {
    mount();
    act(() => {
      startPublish();
    });
    act(() => failPublish());
    expect(dialog().open).toBe(true);
    act(() => void vi.advanceTimersByTime(MIN_OVERLAY_MS));
    expect(dialog().open).toBe(false);
    expect(getPublishState().flash).toBeNull();
  });

  it("Escape (cancel) katmanı kapatmaz; yükleme sürerken kullanıcı iptal edemez", () => {
    mount();
    act(() => {
      startPublish();
    });
    const event = new Event("cancel", { cancelable: true });
    dialog().dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
    expect(dialog().open).toBe(true);
  });
});
