import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, describe, expect, it, vi } from "vitest";
import english from "@/shared/lib/i18n/messages/en.json";
import messages from "@/shared/lib/i18n/messages/tr.json";
import { loadGameResource } from "../services/game-api";
import { GamePrices } from "./game-prices";

vi.mock("../services/game-api", () => ({ loadGameResource: vi.fn() }));
const load = vi.mocked(loadGameResource);
const gameId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
function mount(props: { free?: boolean | null; comingSoon?: boolean | null } = {}, locale = "tr") { render(<NextIntlClientProvider locale={locale} messages={messages}><GamePrices gameId={gameId} {...props} /></NextIntlClientProvider>); }
afterEach(() => { vi.resetAllMocks(); vi.useRealTimers(); });

describe("Pazar ve güncellik bilgili fiyatlar", () => {
  it("teklif ekrandayken süresi dolarsa güncel fiyat olarak göstermez: indirimsiz, bayat rozetli son bilinen fiyat kalır", async () => {
    vi.useFakeTimers(); vi.setSystemTime(new Date("2026-11-07T10:00:00Z"));
    load.mockResolvedValue({ market: { country: "TR", currency: "TRY" }, items: [{ gameId, status: "AVAILABLE", offers: [{ store: "Fixture store", currentPrice: 9, regularPrice: 10, url: "https://store.example/game?affiliate=fixture", fetchedAt: "2026-11-07T09:59:00Z", expiresAt: "2026-11-07T10:00:01Z" }] }] });
    mount();
    await act(async () => { await Promise.resolve(); });
    await act(async () => { await vi.advanceTimersByTimeAsync(1); });
    expect(screen.getByRole("link", { name: messages.Game.openStore })).toHaveAttribute("rel", "sponsored nofollow noopener noreferrer");
    expect(screen.getByText(/9,00/)).toBeVisible();
    await act(async () => { await vi.advanceTimersByTimeAsync(1001); });
    expect(screen.getByTestId("stale-price-note")).toBeVisible();
    expect(screen.getByText(messages.Game.priceStaleBadge)).toBeVisible();
    expect(document.querySelector("del")).toBeNull();
    expect(screen.queryByText(messages.Game.priceStates.STALE)).toBeNull();
  });
  const staleOffer = (store: string, currentPrice: number) => ({ store, currentPrice, regularPrice: 20, url: `https://store.example/${store}`, fetchedAt: "2026-11-09T12:00:00Z", expiresAt: "2026-11-10T12:00:00Z" });
  const stale = (offers = [staleOffer("Epic", 12), staleOffer("Steam", 9)]) => ({ market: { country: "TR", currency: "TRY" }, items: [{ gameId, status: "STALE", offers }] });
  const priceCalls = () => load.mock.calls.filter(([resource]) => resource.kind === "prices").length;
  async function mountAt(iso: string, free?: boolean) {
    vi.useFakeTimers(); vi.setSystemTime(new Date(iso));
    mount({ free });
    await act(async () => { await Promise.resolve(); });
    await act(async () => { await vi.advanceTimersByTimeAsync(1); });
  }
  it("STALE'de son bilinen fiyatları en ucuzdan sıralı, 'güncel olmayabilir' rozeti ve yaş notuyla gösterir; indirim ve eski fiyat yoktur", async () => {
    load.mockResolvedValue(stale());
    await mountAt("2026-11-12T12:00:00Z");
    expect(screen.getByTestId("stale-price-note")).toHaveTextContent("Bu fiyatlar 3 gün önce kontrol edildi ve güncel olmayabilir.");
    expect(screen.getAllByText(messages.Game.priceStaleBadge)).toHaveLength(2);
    expect(screen.getAllByRole("listitem").map((item) => item.querySelector("span.font-semibold")?.textContent)).toEqual(["Steam", "Epic"]);
    expect(document.querySelector("del")).toBeNull();
    expect(screen.queryByText(messages.Game.priceStates.STALE)).toBeNull();
    expect(screen.getAllByRole("link", { name: messages.Game.openStore })).toHaveLength(2);
  });
  it("İngilizce arayüzde bayat fiyat notu ve rozeti İngilizcedir", async () => {
    load.mockResolvedValue({ market: { country: "US", currency: "USD" }, items: [{ gameId, status: "STALE", offers: [staleOffer("Epic", 12)] }] });
    vi.useFakeTimers(); vi.setSystemTime(new Date("2026-11-12T12:00:00Z"));
    render(<NextIntlClientProvider locale="en" messages={english}><GamePrices gameId={gameId} /></NextIntlClientProvider>);
    await act(async () => { await Promise.resolve(); });
    await act(async () => { await vi.advanceTimersByTimeAsync(1); });
    expect(screen.getByTestId("stale-price-note")).toHaveTextContent("These prices were last checked 3 days ago and may be out of date.");
    expect(screen.getByText(english.Game.priceStaleBadge)).toBeVisible();
  });
  it("ücretsiz oyunda bayat fiyat gösterilmez ve yeniden okuma yapılmaz", async () => {
    load.mockResolvedValue(stale());
    await mountAt("2026-11-12T12:00:00Z", true);
    expect(screen.getByText(messages.Game.priceStates.FREE)).toBeVisible();
    expect(screen.queryByTestId("stale-price-note")).toBeNull();
    await act(async () => { await vi.advanceTimersByTimeAsync(60000); });
    expect(priceCalls()).toBe(1);
  });
  it("bayat veya doğrulanmış fiyatsız durumda tam iki kez yeniden okur (12 sn ve 30 sn) ve sonra durur", async () => {
    load.mockResolvedValue(stale());
    await mountAt("2026-11-12T12:00:00Z");
    expect(priceCalls()).toBe(1);
    await act(async () => { await vi.advanceTimersByTimeAsync(11999); });
    expect(priceCalls()).toBe(1);
    await act(async () => { await vi.advanceTimersByTimeAsync(1); });
    expect(priceCalls()).toBe(2);
    await act(async () => { await vi.advanceTimersByTimeAsync(17999); });
    expect(priceCalls()).toBe(2);
    await act(async () => { await vi.advanceTimersByTimeAsync(1); });
    expect(priceCalls()).toBe(3);
    await act(async () => { await vi.advanceTimersByTimeAsync(300000); });
    expect(priceCalls()).toBe(3);
  });
  it("yeniden okumada taze fiyat gelirse bayat not kalkar ve okuma durur", async () => {
    const fresh = { market: { country: "TR", currency: "TRY" }, items: [{ gameId, status: "AVAILABLE", offers: [{ store: "Epic", currentPrice: 12, regularPrice: 12, url: "https://store.example/Epic", fetchedAt: "2026-11-12T12:00:05Z", expiresAt: "2026-11-13T12:00:00Z" }] }] };
    load.mockResolvedValueOnce(stale()).mockResolvedValue(fresh);
    await mountAt("2026-11-12T12:00:00Z");
    expect(screen.getByTestId("stale-price-note")).toBeVisible();
    await act(async () => { await vi.advanceTimersByTimeAsync(12000); });
    await act(async () => { await vi.advanceTimersByTimeAsync(1); });
    expect(screen.queryByTestId("stale-price-note")).toBeNull();
    expect(screen.queryByText(messages.Game.priceStaleBadge)).toBeNull();
    expect(screen.getByText("Epic")).toBeVisible();
    await act(async () => { await vi.advanceTimersByTimeAsync(300000); });
    expect(priceCalls()).toBe(2);
  });
  it("yeniden okuma sırasında bayat liste yanıp sönmez (yükleniyor göstergesi çıkmaz)", async () => {
    load.mockResolvedValue(stale());
    await mountAt("2026-11-12T12:00:00Z");
    await act(async () => { await vi.advanceTimersByTimeAsync(12000); });
    expect(screen.queryByText(messages.Game.loading)).toBeNull();
    expect(screen.getByTestId("stale-price-note")).toBeVisible();
  });
  it("NO_OFFER durumunda yeniden okuma yapılmaz", async () => {
    load.mockResolvedValue({ market: { country: "TR", currency: "TRY" }, items: [{ gameId, status: "NO_OFFER", offers: [] }] });
    await mountAt("2026-11-12T12:00:00Z");
    await act(async () => { await vi.advanceTimersByTimeAsync(120000); });
    expect(priceCalls()).toBe(1);
  });
  it("UNAVAILABLE durumunda iki kez yeniden okunur", async () => {
    load.mockResolvedValue({ market: { country: "TR", currency: "TRY" }, items: [{ gameId, status: "UNAVAILABLE", offers: [] }] });
    await mountAt("2026-11-12T12:00:00Z");
    await act(async () => { await vi.advanceTimersByTimeAsync(12000); });
    await act(async () => { await vi.advanceTimersByTimeAsync(18000); });
    await act(async () => { await vi.advanceTimersByTimeAsync(120000); });
    expect(priceCalls()).toBe(3);
  });
  it("sekme görünmezken yeniden okuma atlanır (deneme hakkı yine tükenir, sürekli sorgu yok)", async () => {
    load.mockResolvedValue(stale());
    const visibility = vi.spyOn(document, "visibilityState", "get").mockReturnValue("hidden");
    try {
      await mountAt("2026-11-12T12:00:00Z");
      await act(async () => { await vi.advanceTimersByTimeAsync(120000); });
      expect(priceCalls()).toBe(1);
    } finally { visibility.mockRestore(); }
  });
  it.each(["NO_OFFER", "UNAVAILABLE", "STALE"])("%s durumunu ücretsiz oyun olarak göstermez", async (status) => {
    load.mockResolvedValue({ market: { country: "TR", currency: "TRY" }, items: [{ gameId, status, offers: [] }] });
    mount();
    await screen.findByText(messages.Game.priceStates[status as keyof typeof messages.Game.priceStates]);
    expect(screen.queryByRole("link", { name: messages.Game.openStore })).toBeNull();
    expect(screen.queryByText(/0,00/)).toBeNull();
  });
  it("ücretsiz oyunda fiyat yokluğunu 'teklif yok' hatası gibi değil, ücretsiz olarak açıklar", async () => {
    load.mockResolvedValue({ market: { country: "TR", currency: "TRY" }, items: [{ gameId, status: "NO_OFFER", offers: [] }] });
    mount({ free: true });
    expect(await screen.findByText(messages.Game.priceStates.FREE)).toBeVisible();
    expect(screen.queryByText(messages.Game.priceStates.NO_OFFER)).toBeNull();
    expect(screen.queryByRole("link", { name: messages.Game.openStore })).toBeNull();
    expect(screen.queryByText(/0,00/)).toBeNull();
  });
  it("henüz çıkmamış oyunda fiyat yokluğunu 'henüz çıkmadı' olarak açıklar; ücretsiz bilgisi öncelikli, bilinmeyende mevcut mesaj kalır", async () => {
    load.mockResolvedValue({ market: { country: "TR", currency: "TRY" }, items: [{ gameId, status: "UNAVAILABLE", offers: [] }] });
    mount({ comingSoon: true });
    expect(await screen.findByText(messages.Game.priceStates.COMING_SOON)).toBeVisible();
    expect(screen.queryByText(messages.Game.priceStates.UNAVAILABLE)).toBeNull();
  });
  it("ön sipariş gibi çıkmamış ama teklifi olan oyunda teklifi gösterir", async () => {
    load.mockResolvedValue({ market: { country: "TR", currency: "TRY" }, items: [{ gameId, status: "AVAILABLE", offers: [{ store: "Steam", currentPrice: 9, regularPrice: 10, url: "https://store.steampowered.com/app/1", fetchedAt: "2020-01-01T00:00:00Z", expiresAt: "2099-01-01T00:00:00Z" }] }] });
    mount({ comingSoon: true });
    expect(await screen.findByText("Steam")).toBeVisible();
    expect(screen.queryByText(messages.Game.priceStates.COMING_SOON)).toBeNull();
  });
  it("farklı pazar yanıtını seçili para birimiyle yeniden etiketlemez", async () => {
    load.mockResolvedValue({ market: { country: "US", currency: "EUR" }, items: [{ gameId, status: "AVAILABLE", offers: [{ store: "wrong market", currentPrice: 9, regularPrice: 10 }] }] });
    mount(); await screen.findByText(messages.Game.priceStates.UNAVAILABLE);
    expect(screen.queryByText("wrong market")).toBeNull();
  });
  it("ülke seçilir (para birimi otomatik gelir; 13 pazar, Türkiye dahil) ve doğru pazarı ister", async () => {
    load.mockResolvedValue({ market: { country: "TR", currency: "TRY" }, items: [{ gameId, status: "NO_OFFER", offers: [] }] });
    mount(); await screen.findByText(messages.Game.priceStates.NO_OFFER);
    expect(screen.queryByLabelText(messages.Game.fields.currency)).toBeNull();
    const select = screen.getByLabelText(messages.Game.fields.country) as HTMLSelectElement;
    expect([...select.options].map((option) => option.value)).toEqual(["USD", "EUR", "AUD", "GBP", "BRL", "CNY", "KRW", "INR", "JPY", "CAD", "PLN", "TRY", "NZD"]);
    expect([...select.options].map((option) => option.textContent)).toEqual(["Amerika Birleşik Devletleri (USD)", "Almanya (EUR)", "Avustralya (AUD)", "Birleşik Krallık (GBP)", "Brezilya (BRL)", "Çin (CNY)", "Güney Kore (KRW)", "Hindistan (INR)", "Japonya (JPY)", "Kanada (CAD)", "Polonya (PLN)", "Türkiye (TRY)", "Yeni Zelanda (NZD)"]);
    fireEvent.change(select, { target: { value: "EUR" } });
    await waitFor(() => expect(select.value).toBe("EUR"));
    expect(load.mock.calls.some(([resource]) => resource.kind === "prices" && resource.country === "DE" && resource.currency === "EUR")).toBe(true);
    fireEvent.change(select, { target: { value: "GBP" } });
    await waitFor(() => expect(select.value).toBe("GBP"));
    expect(load.mock.calls.some(([resource]) => resource.kind === "prices" && resource.country === "GB" && resource.currency === "GBP")).toBe(true);
  });
  it("Türkçe arayüzde varsayılan pazar Türkiye (TRY), İngilizcede ABD (USD)", async () => {
    load.mockResolvedValue({ market: { country: "TR", currency: "TRY" }, items: [{ gameId, status: "NO_OFFER", offers: [] }] });
    mount({}, "tr"); await screen.findByText(messages.Game.priceStates.NO_OFFER);
    expect(load.mock.calls.some(([resource]) => resource.kind === "prices" && resource.country === "TR" && resource.currency === "TRY")).toBe(true);
    expect(load.mock.calls.some(([resource]) => resource.kind === "prices" && resource.currency === "USD")).toBe(false);
  });
  it("İngilizce arayüzde varsayılan pazar ABD (USD) ve Türkiye notu görünmez", async () => {
    load.mockResolvedValue({ market: { country: "US", currency: "USD" }, items: [{ gameId, status: "NO_OFFER", offers: [] }] });
    mount({}, "en"); await screen.findByText(messages.Game.priceStates.NO_OFFER);
    expect(load.mock.calls.some(([resource]) => resource.kind === "prices" && resource.country === "US" && resource.currency === "USD")).toBe(true);
    expect(screen.queryByTestId("try-note")).toBeNull();
  });
  it("Türkiye pazarında şeffaflık notu görünür: Steam fiyatı için ABD pazarı, dönüştürülmüş fiyatlar", async () => {
    load.mockResolvedValue({ market: { country: "TR", currency: "TRY" }, items: [{ gameId, status: "NO_OFFER", offers: [] }] });
    mount(); await screen.findByText(messages.Game.priceStates.NO_OFFER);
    expect(screen.getByTestId("try-note")).toHaveTextContent(messages.Game.priceTurkeyNote);
    expect(messages.Game.priceTurkeyNote).toMatch(/ABD/);
    expect(messages.Game.priceTurkeyNote).toMatch(/dönüştürül/);
  });
  it("her oyunda tüm mağazalar gösterilir ve en ucuzdan pahalıya sıralanır", async () => {
    const offer = (store: string, currentPrice: number) => ({ store, currentPrice, regularPrice: 500, url: `https://store.example/${store}`, fetchedAt: "2020-01-01T00:00:00Z", expiresAt: "2099-01-01T00:00:00Z" });
    load.mockResolvedValue({ market: { country: "TR", currency: "TRY" }, items: [{ gameId, status: "AVAILABLE", offers: [offer("Epic", 230), offer("Steam", 320), offer("GOG", 250), offer("Fanatical", 280), offer("Humble", 285), offer("GMG", 260), offer("IndieGala", 270)] }] });
    mount();
    await screen.findByText("Epic");
    const stores = screen.getAllByRole("listitem").map((item) => item.querySelector("span.font-semibold")?.textContent);
    expect(stores).toEqual(["Epic", "GOG", "GMG", "IndieGala", "Fanatical", "Humble", "Steam"]);
  });
});
