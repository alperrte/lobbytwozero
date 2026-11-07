import { act, render, screen } from "@testing-library/react";
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
    load.mockResolvedValue({ market: { country: "TR", currency: "TRY" }, items: [{ gameId, status: "AVAILABLE", offers: [{ store: "Fixture store", currentPrice: 9, regularPrice: 10, url: "https://store.example/game?affiliate=fixture", fetchedAt: "2026-11-07T09:59:00Z", expiresAt: "2026-11-07T10:00:01Z" }] }] });
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
    load.mockResolvedValue({ market: { country: "TR", currency: "TRY" }, items: [{ gameId, status, offers: [] }] });
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
});
