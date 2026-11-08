import { act, fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { FADE_MS, FLASH_DELAY_MS, FLASH_MS, MIN_OVERLAY_MS, completePublish, failPublish, finishPublish, getPublishState, resetPublishFlow, startPublish } from "../services/publish-flow";
import { PublishToast } from "./publish-toast";

function mount() {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr}>
      <PublishToast />
    </NextIntlClientProvider>
  );
}

const advance = (ms: number) => act(() => void vi.advanceTimersByTime(ms));
/** Paylaşım tamamlanır; katman en az MIN_OVERLAY_MS açık kalır, bildirim ardından giriş animasyonundan sonra (FLASH_DELAY_MS) gelir. */
function publish(postId: string) {
  act(() => {
    startPublish();
    completePublish(postId);
    finishPublish();
  });
  advance(MIN_OVERLAY_MS + FLASH_DELAY_MS);
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(0);
});
afterEach(() => {
  act(() => resetPublishFlow());
  vi.useRealTimers();
});

describe("PublishToast (sağ alt başarı bildirimi)", () => {
  it("paylaşım yokken hiçbir şey göstermez; yalnız backend onayından sonra gelir", () => {
    mount();
    expect(screen.queryByRole("status")).toBeNull();
    act(() => {
      startPublish();
      completePublish("p1");
      finishPublish();
    });
    expect(screen.queryByRole("status")).toBeNull();
    advance(MIN_OVERLAY_MS);
    expect(screen.queryByRole("status")).toBeNull();
    advance(FLASH_DELAY_MS);
    expect(screen.getByRole("status")).toHaveTextContent("Gönderin paylaşıldı.");
  });

  it("ekranın sağ altına sabitlenir", () => {
    mount();
    publish("p1");
    const wrapper = screen.getByRole("status").parentElement as HTMLElement;
    expect(wrapper.className).toContain("fixed");
    expect(wrapper.className).toContain("bottom-");
    expect(wrapper.className).toContain("sm:end-6");
  });

  it("2 sn sonra yumuşak geçişle kaybolur ve depodaki kayıt silinir", () => {
    mount();
    publish("p1");
    advance(FLASH_MS - 100);
    expect(screen.getByRole("status").className).not.toContain("opacity-0");
    advance(150);
    expect(screen.getByRole("status").className).toContain("opacity-0");
    expect(getPublishState().flash).not.toBeNull();
    advance(FADE_MS);
    expect(screen.queryByRole("status")).toBeNull();
    expect(getPublishState().flash).toBeNull();
  });

  it("kapat düğmesi bildirimi geçişle hemen kaldırır", () => {
    mount();
    publish("p1");
    fireEvent.click(screen.getByRole("button", { name: "Bildirimi kapat" }));
    expect(screen.getByRole("status").className).toContain("opacity-0");
    advance(FADE_MS);
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("fare üzerindeyken de süre dolunca kaybolur (kalıcı kalmaz)", () => {
    mount();
    publish("p1");
    fireEvent.mouseEnter(screen.getByRole("status"));
    advance(FLASH_MS + 50);
    advance(FADE_MS);
    expect(screen.queryByRole("status")).toBeNull();
    expect(getPublishState().flash).toBeNull();
  });

  it("başarısız paylaşımda bildirim kurulmaz", () => {
    mount();
    act(() => {
      startPublish();
      failPublish();
    });
    advance(MIN_OVERLAY_MS + FLASH_DELAY_MS + FLASH_MS);
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("bileşen kapanınca zamanlayıcılar temizlenir", () => {
    const view = mount();
    publish("p1");
    view.unmount();
    expect(() => advance(10_000)).not.toThrow();
  });
});
