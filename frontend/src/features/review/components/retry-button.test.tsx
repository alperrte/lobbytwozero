import { act, fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { RetryButton } from "./retry-button";

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

function view(error: unknown, onRetry = vi.fn(), locale: "tr" | "en" = "tr") {
  render(
    <NextIntlClientProvider locale={locale} messages={locale === "tr" ? tr : en}>
      <RetryButton error={error} onRetry={onRetry} />
    </NextIntlClientProvider>
  );
  return onRetry;
}

describe("Oyun kartı 'tekrar dene' (Game batch 429)", () => {
  it("Retry-After süresince kapalıdır, kalan saniyeyi söyler ve süre dolunca açılır", () => {
    const onRetry = view(new ApiHttpError(429, { retryAfterSeconds: 3 }));
    const button = screen.getByRole("button", { name: "3 sn sonra tekrar dene" });
    expect(button).toBeDisabled();
    fireEvent.click(button);
    expect(onRetry).not.toHaveBeenCalled();
    act(() => { vi.advanceTimersByTime(3100); });
    const ready = screen.getByRole("button", { name: "Tekrar dene" });
    expect(ready).toBeEnabled();
    fireEvent.click(ready);
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it("429 olmayan hatada (503) hemen etkindir; otomatik yeniden deneme yapılmaz", () => {
    const onRetry = view(new ApiHttpError(503));
    act(() => { vi.advanceTimersByTime(10000); });
    expect(onRetry).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Tekrar dene" })).toBeEnabled();
  });

  it("Retry-After başlığı yoksa 429 da hemen etkindir (süre uydurulmaz)", () => {
    view(new ApiHttpError(429));
    expect(screen.getByRole("button", { name: "Tekrar dene" })).toBeEnabled();
  });

  it("İngilizce metin kullanır", () => {
    view(new ApiHttpError(429, { retryAfterSeconds: 5 }), vi.fn(), "en");
    expect(screen.getByRole("button", { name: "Try again in 5s" })).toBeDisabled();
  });
});
