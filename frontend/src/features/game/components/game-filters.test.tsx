import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import messages from "@/shared/lib/i18n/messages/tr.json";
import enMessages from "@/shared/lib/i18n/messages/en.json";
import { ApiHttpError } from "@/shared/lib/api/client";
import { loadGameResource } from "../services/game-api";
import type { GameResource, TermList } from "../types/game";
import { GameActiveFilters, GameFilters } from "./game-filters";

vi.mock("../services/game-api", async (original) => ({ ...await original<typeof import("../services/game-api")>(), loadGameResource: vi.fn() }));
const load = vi.mocked(loadGameResource);
const t = messages.Game;
const genres: TermList = { kind: "GENRE", items: [{ code: "role-playing-rpg", nameTr: null, nameEn: "Role-playing (RPG)", gameCount: 40 }, { code: "adventure", nameTr: "Macera", nameEn: "Adventure", gameCount: 12 }] };
const platforms: TermList = { kind: "PLATFORM", items: [{ code: "win", nameTr: null, nameEn: "PC (Microsoft Windows)", gameCount: 90 }, { code: "ps5", nameTr: null, nameEn: null, gameCount: 5 }] };

function serve(terms: (resource: Extract<GameResource, { kind: "terms" }>) => unknown) {
  load.mockImplementation(async (resource) => {
    if (resource.kind === "terms") return terms(resource);
    return undefined;
  });
}
function mount(onApply = vi.fn()) {
  render(<NextIntlClientProvider locale="tr" messages={messages}><GameFilters params={new URLSearchParams()} onApply={onApply} /></NextIntlClientProvider>);
  fireEvent.click(screen.getByRole("button", { name: t.filters }));
  return onApply;
}
beforeEach(() => serve((resource) => (resource.termKind === "GENRE" ? genres : platforms)));
afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe("Filtre paneli: tür, platform ve dil açılır listeleri", () => {
  it("terimleri yalnız panel açılınca ve türü/platformu ayrı ayrı gerçek uçtan ister", async () => {
    render(<NextIntlClientProvider locale="tr" messages={messages}><GameFilters params={new URLSearchParams()} onApply={vi.fn()} /></NextIntlClientProvider>);
    expect(load).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: t.filters }));
    await waitFor(() => expect(load.mock.calls.map(([resource]) => JSON.stringify(resource)).sort()).toEqual([
      JSON.stringify({ kind: "terms", termKind: "GENRE" }), JSON.stringify({ kind: "terms", termKind: "PLATFORM" })].sort()));
  });
  it("tür listesi gerçek terimleri etiketle (Türkçe ad yoksa İngilizce, o da yoksa kod) gösterir; seçim kodu filtreye yazar", async () => {
    const onApply = mount();
    const genre = screen.getByLabelText(t.fields.genre);
    await waitFor(() => expect(genre).toHaveAttribute("role", "combobox"));
    fireEvent.focus(genre);
    expect(within(screen.getByRole("listbox")).getAllByRole("option").map((option) => option.textContent)).toEqual(["Role-playing (RPG)role-playing-rpg", "Macera" + "adventure"]);
    fireEvent.change(genre, { target: { value: "mace" } });
    expect(within(screen.getByRole("listbox")).getAllByRole("option")).toHaveLength(1);
    fireEvent.click(screen.getByRole("option", { name: /Macera/ }));
    fireEvent.click(screen.getByRole("button", { name: t.applyFilters }));
    expect(onApply).toHaveBeenCalledWith(expect.objectContaining({ genre: "adventure" }));
  });
  it("platform listesi kodu etiket olarak kullanır (ad yoksa) ve doğru kodu gönderir", async () => {
    const onApply = mount();
    const platform = screen.getByLabelText(t.fields.platform);
    await waitFor(() => expect(platform).toHaveAttribute("role", "combobox"));
    fireEvent.focus(platform);
    expect(within(screen.getByRole("listbox")).getAllByRole("option").map((option) => option.textContent)).toEqual(["PC (Microsoft Windows)win", "ps5ps5"]);
    fireEvent.click(screen.getByRole("option", { name: /PC \(Microsoft Windows\)/ }));
    fireEvent.click(screen.getByRole("button", { name: t.applyFilters }));
    expect(onApply).toHaveBeenCalledWith(expect.objectContaining({ platform: "win" }));
  });
  it("terim ucu hata verirse veya boşsa seçenek uydurulmaz; alan serbest metin olarak çalışır", async () => {
    serve(() => { throw new ApiHttpError(503); });
    const onApply = mount();
    const genre = screen.getByLabelText(t.fields.genre);
    await waitFor(() => expect(load).toHaveBeenCalled());
    expect(genre).not.toHaveAttribute("role", "combobox");
    expect(screen.queryByRole("listbox")).toBeNull();
    fireEvent.change(genre, { target: { value: "rpg" } });
    fireEvent.click(screen.getByRole("button", { name: t.applyFilters }));
    expect(onApply).toHaveBeenCalledWith(expect.objectContaining({ genre: "rpg" }));
  });
  it("boş terim listesi de düz metin girişidir", async () => {
    serve((resource) => ({ kind: resource.termKind, items: [] }));
    mount();
    await waitFor(() => expect(load).toHaveBeenCalledTimes(2));
    expect(screen.getByLabelText(t.fields.platform)).not.toHaveAttribute("role", "combobox");
  });
  it("dil listesi yalnız doğrulanmış gerçek kodları sunar ve kodu gönderir", async () => {
    const onApply = mount();
    const language = screen.getByLabelText(t.fields.language);
    fireEvent.focus(language);
    fireEvent.change(language, { target: { value: "türk" } });
    fireEvent.click(screen.getByRole("option", { name: /tr-tr/ }));
    fireEvent.click(screen.getByRole("button", { name: t.applyFilters }));
    expect(onApply).toHaveBeenCalledWith(expect.objectContaining({ language: "tr-tr" }));
  });
  it("yardım metni gerçek dil kodu biçimini (tr-tr, en-us) anlatır; eski yanlış örneği (tr veya en) içermez", () => {
    mount();
    const help = screen.getByText(t.filterHelp);
    expect(help).toHaveTextContent("tr-tr");
    expect(help).toHaveTextContent("en-us");
    expect(help.textContent).not.toContain("tr veya en gibi");
  });
  it("fiyat pazarı ülke adıyla sunulur (13 pazar, Türkiye dahil), para birimi otomatik gelir; seçim ülkeyi birlikte filtreye yazar", () => {
    const onApply = vi.fn();
    render(<NextIntlClientProvider locale="tr" messages={messages}><GameFilters params={new URLSearchParams("currency=TRY&country=TR")} onApply={onApply} /></NextIntlClientProvider>);
    fireEvent.click(screen.getByRole("button", { name: t.filters }));
    expect(screen.queryByLabelText(t.fields.currency)).toBeNull();
    const select = screen.getByLabelText(t.fields.country) as HTMLSelectElement;
    expect([...select.options].map((option) => option.value)).toEqual(["USD", "EUR", "AUD", "GBP", "BRL", "CNY", "KRW", "INR", "JPY", "CAD", "PLN", "TRY", "NZD"]);
    expect([...select.options].map((option) => option.textContent)).toEqual(["Amerika Birleşik Devletleri (USD)", "Almanya (EUR)", "Avustralya (AUD)", "Birleşik Krallık (GBP)", "Brezilya (BRL)", "Çin (CNY)", "Güney Kore (KRW)", "Hindistan (INR)", "Japonya (JPY)", "Kanada (CAD)", "Polonya (PLN)", "Türkiye (TRY)", "Yeni Zelanda (NZD)"]);
    expect(select.value).toBe("TRY");
    fireEvent.change(select, { target: { value: "GBP" } });
    fireEvent.click(screen.getByRole("button", { name: t.applyFilters }));
    expect(onApply).toHaveBeenCalledWith(expect.objectContaining({ currency: "GBP", country: "GB" }));
  });
  it("URL'de pazar yoksa Türkçede varsayılan Türkiye'dir ve varsayılan seçim URL'ye yazılmaz; İngilizcede varsayılan ABD'dir", () => {
    const onApply = vi.fn();
    const { unmount } = render(<NextIntlClientProvider locale="tr" messages={messages}><GameFilters params={new URLSearchParams()} onApply={onApply} /></NextIntlClientProvider>);
    fireEvent.click(screen.getByRole("button", { name: t.filters }));
    expect((screen.getByLabelText(t.fields.country) as HTMLSelectElement).value).toBe("TRY");
    fireEvent.click(screen.getByRole("button", { name: t.applyFilters }));
    expect(onApply).toHaveBeenCalledWith(expect.objectContaining({ currency: "", country: "" }));
    unmount();
    render(<NextIntlClientProvider locale="en" messages={enMessages}><GameFilters params={new URLSearchParams()} onApply={onApply} /></NextIntlClientProvider>);
    fireEvent.click(screen.getByRole("button", { name: enMessages.Game.filters }));
    expect((screen.getByLabelText(enMessages.Game.fields.country) as HTMLSelectElement).value).toBe("USD");
  });
  it("+18 oyunları göster seçeneği işaretlenince filtreye yazılır, işaretsizken gönderilmez ve aktif filtre olarak kaldırılabilir", () => {
    const onApply = vi.fn();
    render(<NextIntlClientProvider locale="tr" messages={messages}><GameFilters params={new URLSearchParams()} onApply={onApply} /></NextIntlClientProvider>);
    fireEvent.click(screen.getByRole("button", { name: t.filters }));
    const box = screen.getByRole("checkbox", { name: t.fields.includeAdult }) as HTMLInputElement;
    expect(box.checked).toBe(false);
    fireEvent.click(screen.getByRole("button", { name: t.applyFilters }));
    expect(onApply).toHaveBeenLastCalledWith(expect.objectContaining({ includeAdult: "" }));
    fireEvent.click(box);
    fireEvent.click(screen.getByRole("button", { name: t.applyFilters }));
    expect(onApply).toHaveBeenLastCalledWith(expect.objectContaining({ includeAdult: "true" }));
  });
  it("+18 seçimi aktif filtre çipi olarak görünür ve tek başına kaldırılabilir", () => {
    const onChange = vi.fn();
    render(<NextIntlClientProvider locale="tr" messages={messages}><GameActiveFilters params={new URLSearchParams("includeAdult=true")} onChange={onChange} /></NextIntlClientProvider>);
    fireEvent.click(screen.getByRole("button", { name: (accessibleName) => accessibleName.includes(t.fields.includeAdult) }));
    expect(onChange).toHaveBeenCalledWith({ includeAdult: "", page: "" });
  });
});
