import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, describe, expect, it, vi } from "vitest";
import messages from "@/shared/lib/i18n/messages/tr.json";
import { loadGameResource } from "../services/game-api";
import { GamePrices } from "./game-prices";

vi.mock("../services/game-api", () => ({ loadGameResource: vi.fn() }));
const load = vi.mocked(loadGameResource);
const gameId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
function mount() { render(<NextIntlClientProvider locale="tr" messages={messages}><GamePrices gameId={gameId} /></NextIntlClientProvider>); }
afterEach(() => { vi.resetAllMocks(); vi.useRealTimers(); });

describe("Pazar ve güncellik bilgili fiyatlar", () => {
  it("teklif ekrandayken süresi dolarsa güncel fiyatı ve satın alma bağlantısını kaldırır", async () => {
    vi.useFakeTimers(); vi.setSystemTime(new Date("2026-11-07T10:00:00Z"));
    load.mockResolvedValue({ market: { country: "US", currency: "USD" }, items: [{ gameId, status: "AVAILABLE", offers: [{ store: "Fixture store", currentPrice: 9, regularPrice: 10, url: "https://store.example/game?affiliate=fixture", fetchedAt: "2026-11-07T09:59:00Z", expiresAt: "2026-11-07T10:00:01Z" }] }] });
    mount();
    await act(async () => { await Promise.resolve(); });
    await act(async () => { await vi.advanceTimersByTimeAsync(1); });
    expect(screen.getByRole("link", { name: messages.Game.openStore })).toHaveAttribute("rel", "sponsored nofollow noopener noreferrer");
    expect(screen.getByText(/9,00/)).toBeVisible();
    await act(async () => { await vi.advanceTimersByTimeAsync(1001); });
    expect(screen.queryByRole("link", { name: messages.Game.openStore })).toBeNull();
    expect(screen.getByText(messages.Game.priceStates.STALE)).toBeVisible();
  });
  it.each(["NO_OFFER", "UNAVAILABLE", "STALE"])("%s durumunu ücretsiz oyun olarak göstermez", async (status) => {
    load.mockResolvedValue({ market: { country: "US", currency: "USD" }, items: [{ gameId, status, offers: [] }] });
    mount();
    await screen.findByText(messages.Game.priceStates[status as keyof typeof messages.Game.priceStates]);
    expect(screen.queryByRole("link", { name: messages.Game.openStore })).toBeNull();
    expect(screen.queryByText(/0,00/)).toBeNull();
  });
  it("farklı pazar yanıtını seçili para birimiyle yeniden etiketlemez", async () => {
    load.mockResolvedValue({ market: { country: "US", currency: "EUR" }, items: [{ gameId, status: "AVAILABLE", offers: [{ store: "wrong market", currentPrice: 9, regularPrice: 10 }] }] });
    mount(); await screen.findByText(messages.Game.priceStates.UNAVAILABLE);
    expect(screen.queryByText("wrong market")).toBeNull();
  });
  it("ülke seçilir (para birimi otomatik gelir; doğrulanmış 12 pazar, TRY yok) ve doğru pazarı ister", async () => {
    load.mockResolvedValue({ market: { country: "US", currency: "USD" }, items: [{ gameId, status: "NO_OFFER", offers: [] }] });
    mount(); await screen.findByText(messages.Game.priceStates.NO_OFFER);
    expect(screen.queryByLabelText(messages.Game.fields.currency)).toBeNull();
    const select = screen.getByLabelText(messages.Game.fields.country) as HTMLSelectElement;
    expect([...select.options].map((option) => option.value)).toEqual(["USD", "EUR", "AUD", "GBP", "BRL", "CNY", "KRW", "INR", "JPY", "CAD", "PLN", "NZD"]);
    expect([...select.options].map((option) => option.textContent)).toEqual(["Amerika Birleşik Devletleri (USD)", "Almanya (EUR)", "Avustralya (AUD)", "Birleşik Krallık (GBP)", "Brezilya (BRL)", "Çin (CNY)", "Güney Kore (KRW)", "Hindistan (INR)", "Japonya (JPY)", "Kanada (CAD)", "Polonya (PLN)", "Yeni Zelanda (NZD)"]);
    fireEvent.change(select, { target: { value: "EUR" } });
    await waitFor(() => expect(select.value).toBe("EUR"));
    expect(load.mock.calls.some(([resource]) => resource.kind === "prices" && resource.country === "DE" && resource.currency === "EUR")).toBe(true);
    fireEvent.change(select, { target: { value: "GBP" } });
    await waitFor(() => expect(select.value).toBe("GBP"));
    expect(load.mock.calls.some(([resource]) => resource.kind === "prices" && resource.country === "GB" && resource.currency === "GBP")).toBe(true);
  });
});
