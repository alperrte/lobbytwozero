import { act, fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { createElement, type ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { FADE_MS, FLASH_DELAY_MS, FLASH_MS, MIN_OVERLAY_MS, completePublish, finishPublish, getPublishState, resetPublishFlow, startPublish } from "../services/publish-flow";
import { PublishNotice } from "./publish-notice";

vi.mock("@/shared/lib/i18n/navigation", () => ({
  Link: ({ href, children, ...rest }: { href: unknown; children: ReactNode }) =>
    createElement("a", { href: typeof href === "string" ? href : JSON.stringify(href), ...rest }, children)
}));

function mount() {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr}>
      <PublishNotice />
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

describe("PublishNotice (2 saniyelik başarı bildirimi)", () => {
  it("paylaşım yokken hiçbir şey göstermez; tamamlanınca önce animasyon, ardından bildirim gelir", () => {
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
    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("görünür, 'Gönderiyi aç' ve 'Kapat' kullanılabilir; 2 sn sonra yumuşak geçişle kaybolur", () => {
    mount();
    publish("p1");
    expect(screen.getByRole("status")).toHaveTextContent("Gönderin paylaşıldı.");
    expect(screen.getByRole("link", { name: "Gönderiyi aç" })).toHaveAttribute("href", expect.stringContaining("p1"));
    expect(screen.getByRole("button", { name: "Kapat" })).toBeInTheDocument();

    advance(FLASH_MS - 100);
    expect(screen.getByRole("status").className).not.toContain("opacity-0");
    advance(150);
    expect(screen.getByRole("status").className).toContain("opacity-0");
    expect(getPublishState().flash).not.toBeNull();
    advance(FADE_MS);
    expect(screen.queryByRole("status")).toBeNull();
    expect(getPublishState().flash).toBeNull();
  });

  it("'Kapat' bildirimi geçişle hemen kaldırır", () => {
    mount();
    publish("p1");
    fireEvent.click(screen.getByRole("button", { name: "Kapat" }));
    expect(screen.getByRole("status").className).toContain("opacity-0");
    advance(300);
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("her yeni başarılı paylaşım süreyi baştan başlatır", () => {
    mount();
    publish("p1");
    advance(1500);
    publish("p2");
    expect(screen.getByRole("link", { name: "Gönderiyi aç" })).toHaveAttribute("href", expect.stringContaining("p2"));
    advance(FLASH_MS - 100);
    expect(screen.getByRole("status").className).not.toContain("opacity-0");
    advance(150);
    expect(screen.getByRole("status").className).toContain("opacity-0");
    advance(FADE_MS);
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("fare bildirimin üzerindeyken de süre dolunca kaybolur (kalıcı kalmaz)", () => {
    mount();
    publish("p1");
    fireEvent.mouseEnter(screen.getByRole("status"));
    advance(FLASH_MS + 50);
    advance(FADE_MS);
    expect(screen.queryByRole("status")).toBeNull();
    expect(getPublishState().flash).toBeNull();
  });

  it("bileşen kapanınca zamanlayıcılar temizlenir (kaldırma sonrası güncelleme yok)", () => {
    const view = mount();
    publish("p1");
    view.unmount();
    expect(() => advance(10_000)).not.toThrow();
  });
});
