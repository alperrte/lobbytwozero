import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, describe, expect, it, vi } from "vitest";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { loadGameResource } from "../services/game-api";
import type { GameDetail as Detail, GameResource } from "../types/game";
import { GameDetail } from "./game-detail";

vi.mock("@/shared/lib/i18n/navigation", () => ({
  Link: ({ href, children, className }: { href: string | { pathname: string; query?: Record<string, string> }; children: React.ReactNode; className?: string }) => <a className={className} href={typeof href === "string" ? href : `${href.pathname}?${new URLSearchParams(href.query).toString()}`}>{children}</a>
}));
vi.mock("../services/game-api", async (original) => ({ ...await original<typeof import("../services/game-api")>(), loadGameResource: vi.fn(), mutateGame: vi.fn() }));
const load = vi.mocked(loadGameResource);
const gameId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const base: Detail = {
  game: { gameId, title: "Fixture Quest", origin: "EXTERNAL", metadataCompleteness: "FULL", gameType: "MAIN_GAME", coverUrl: "https://images.example/cover.jpg" },
  description: "Katalogdan alınan açıklama.", releaseDate: "2026-03-05", freshness: { status: "STALE", fetchedAt: "2026-11-01T10:00:00Z" },
  metadata: {
    developers: ["Studio A"], publishers: ["Publisher B"], languages: ["tr"], earlyAccess: true,
    genres: [{ id: "1", code: "rpg", nameTr: "Rol yapma", nameEn: "Role-playing" }], platforms: [{ id: "2", code: "pc", nameTr: "Bilgisayar", nameEn: "PC" }],
    categories: [{ id: "3", code: "single", nameTr: "Tek oyunculu", nameEn: "Single-player" }], tags: [{ id: "4", code: "indie", nameTr: "Bağımsız", nameEn: "Indie" }],
    languageSupport: [{ languageCode: "tr", interfaceSupport: "SUPPORTED", audioSupport: "UNKNOWN", subtitleSupport: "UNSUPPORTED" }],
    media: [{ type: "SCREENSHOT", url: "https://images.example/s1.jpg", provider: "IGDB", sortOrder: 1 }, { type: "TRAILER", url: "https://video.example/t", provider: "IGDB", sortOrder: 2 }],
    systemRequirements: [{ level: "MINIMUM", platform: "Windows", os: "Windows 10", cpu: "i5", gpu: null, ramMb: 8192, storageMb: 512, rawText: null }, { level: "RECOMMENDED", platform: "Windows", os: null, cpu: null, gpu: "RTX", ramMb: 16384, storageMb: 51200, rawText: null }]
  }
};
function serve(detail: Detail | Error = base) {
  load.mockImplementation(async (resource: GameResource) => {
    if (resource.kind === "detail") { if (detail instanceof Error) throw detail; return detail; }
    if (resource.kind === "session") return null;
    if (resource.kind === "prices") return { market: { country: "US", currency: "USD" }, items: [{ gameId, status: "NO_OFFER", offers: [] }] };
    if (resource.kind === "publicDeveloper") return { gameId, description: { text: "Geliştirici notu", updatedAt: "2026-02-01T00:00:00Z" }, media: [{ id: "m1", type: "VIDEO", url: "https://video.example/dev", altText: null, createdAt: "2026-02-01T00:00:00Z" }], updateNotes: [{ id: "n1", title: "1.1 yaması", body: "Hata düzeltmeleri", createdAt: "2026-03-01T00:00:00Z", updatedAt: "2026-03-01T00:00:00Z" }] };
    if (resource.kind === "module") return { kind: "achievements", items: [], totalElements: 0, page: 0, size: 20 };
    return undefined;
  });
}
function mount(locale: "tr" | "en" = "tr") { return render(<NextIntlClientProvider locale={locale} messages={locale === "tr" ? tr : en}><GameDetail gameId={gameId} /></NextIntlClientProvider>); }
afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe("Oyun detayı", () => {
  it("hero bölümünde başlık, tür, çıkış tarihi, geliştirici ve platform gösterir", async () => {
    serve(); mount();
    expect(await screen.findByRole("heading", { level: 1, name: "Fixture Quest" })).toBeVisible();
    expect(screen.getAllByText(tr.Game.earlyAccessBadge).length).toBeGreaterThan(0);
    expect(screen.getAllByText("Studio A").length).toBeGreaterThan(0);
    expect(screen.getByText("5 Mar 2026")).toBeVisible();
    expect(screen.getAllByText("Bilgisayar").length).toBeGreaterThan(0);
  });
  it("bölüm gezinmesi sayfa içi bağlantılar sunar", async () => {
    serve(); mount(); await screen.findByRole("heading", { level: 1 });
    const nav = within(screen.getByRole("navigation", { name: tr.Game.pageSections }));
    expect(nav.getAllByRole("link").map((link) => link.getAttribute("href"))).toEqual(["#game-overview", "#game-information", "#game-prices", "#game-developer", "#game-community", "#game-feedback"]);
  });
  it("tür ve platform terimlerini katalog süzgecine bağlar; kategori ve etiketi karıştırmaz", async () => {
    serve(); mount(); await screen.findByRole("heading", { level: 1 });
    expect(screen.getByRole("link", { name: "Rol yapma" })).toHaveAttribute("href", "/games?genre=rpg");
    expect(screen.getByRole("link", { name: "Bilgisayar" })).toHaveAttribute("href", "/games?platform=pc");
    expect(screen.queryByRole("link", { name: "Tek oyunculu" })).toBeNull();
    expect(screen.getByText("Tek oyunculu")).toBeVisible();
    expect(screen.getByText("Bağımsız")).toBeVisible();
  });
  it("sistem gereksinimi birimlerini çevirir ve eksik değeri uydurmaz", async () => {
    serve(); mount(); await screen.findByRole("heading", { level: 1 });
    const minimum = within(screen.getByRole("region", { name: /Windows — Minimum/ }));
    expect(minimum.getByText("8 GB")).toBeVisible();
    expect(minimum.getByText("512 MB")).toBeVisible();
    expect(minimum.queryByText(tr.Game.fields.gpu + ":")).toBeNull();
    const recommended = within(screen.getByRole("region", { name: /Windows — Önerilen/ }));
    expect(recommended.getByText("50 GB")).toBeVisible();
    expect(recommended.queryByText(tr.Game.fields.os + ":")).toBeNull();
  });
  it("bilinmeyen dil desteğini desteklenmiyor olarak göstermez", async () => {
    serve(); mount(); await screen.findByRole("heading", { level: 1 });
    const row = within(screen.getByRole("row", { name: /^Türkçe/ }));
    expect(row.getAllByRole("cell").map((cell) => cell.textContent)).toEqual([tr.Game.support.SUPPORTED, tr.Game.support.UNKNOWN, tr.Game.support.UNSUPPORTED]);
  });
  it("veri güncelliğini ve STALE durumunu belirtir", async () => {
    serve(); mount(); await screen.findByRole("heading", { level: 1 });
    expect(screen.getByText(new RegExp(tr.Game.freshness.STALE))).toBeVisible();
  });
  it("galeriye kapak ve görselleri koyar; fragmanı harici bağlantı yapar", async () => {
    serve(); mount(); await screen.findByRole("heading", { level: 1 });
    const gallery = screen.getByRole("group", { name: tr.Game.gallery });
    expect(within(gallery).getAllByRole("img").length).toBe(1);
    const hrefs = screen.getAllByRole("link", { name: new RegExp(tr.Game.openVideo) }).map((link) => link.getAttribute("href"));
    expect(hrefs).toContain("https://video.example/t");
  });
  it("kapak ve metadata eksik olduğunda yer tutucu ve bilinmiyor gösterir", async () => {
    serve({ ...base, game: { ...base.game, coverUrl: null }, description: null, releaseDate: null, freshness: { status: "MISSING", fetchedAt: null }, metadata: { developers: [], publishers: [], languages: [], genres: [], platforms: [] } });
    mount(); await screen.findByRole("heading", { level: 1 });
    expect(screen.getByText(tr.Game.missingDescription)).toBeVisible();
    expect(screen.getByText(tr.Game.releaseUnknown)).toBeVisible();
    expect(screen.getByTestId("cover-placeholder")).toBeInTheDocument();
    expect(screen.queryByText(tr.Game.requirements)).toBeNull();
    expect(screen.queryByText(tr.Game.languageSupport)).toBeNull();
  });
  it("uzun açıklamayı genişletilebilir yapar", async () => {
    serve({ ...base, description: "Uzun açıklama. ".repeat(80) }); mount(); await screen.findByRole("heading", { level: 1 });
    const toggle = screen.getByRole("button", { name: tr.Game.showMore });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(toggle);
    expect(screen.getByRole("button", { name: tr.Game.showLess })).toHaveAttribute("aria-expanded", "true");
  });
  it("geliştirici içeriğini katalog verisinden ayrı ve etiketli gösterir", async () => {
    serve(); mount();
    const section = within(await screen.findByRole("region", { name: new RegExp(tr.Game.developerContent) }));
    expect(await section.findByText(tr.Game.providedByDeveloper)).toBeVisible();
    expect(section.getByText("1.1 yaması")).toBeVisible();
    expect(section.getByRole("link", { name: new RegExp(tr.Game.openVideo) })).toHaveAttribute("href", "https://video.example/dev");
  });
  it("fiyat NO_OFFER durumunu ücretsiz göstermez", async () => {
    serve(); mount();
    expect(await screen.findByText(tr.Game.priceStates.NO_OFFER)).toBeVisible();
    expect(screen.queryByText(/0,00/)).toBeNull();
  });
  it("İngilizce arayüzde aynı veriyi İngilizce terimlerle gösterir", async () => {
    serve(); mount("en");
    expect(await screen.findByRole("heading", { level: 1, name: "Fixture Quest" })).toBeVisible();
    expect(screen.getByRole("link", { name: "Role-playing" })).toBeVisible();
    expect(screen.getByRole("navigation", { name: en.Game.pageSections })).toBeVisible();
    expect(screen.getByText(en.Game.freshness.STALE, { exact: false })).toBeVisible();
  });
  it("404'te anlaşılır hata ve yeniden deneme sunar", async () => {
    const { ApiHttpError } = await import("@/shared/lib/api/client");
    serve(new ApiHttpError(404)); mount();
    expect(await screen.findByText(tr.Game.errors["404"])).toBeVisible();
    expect(screen.queryByRole("heading", { level: 1 })).toBeNull();
  });
  it("gerçek API'de ad alanları null iken terimleri kod olarak gösterir; kapak galeride tekrarlanmaz", async () => {
    const realShape: Detail = { ...base, metadata: { ...base.metadata, genres: [{ id: "g", code: "rpg", nameTr: null, nameEn: null }], platforms: [{ id: "p", code: "pc", nameTr: null, nameEn: null }], languageSupport: [], systemRequirements: [],
      media: [{ type: "COVER", url: "https://images.example/cover.jpg", provider: "ADMIN", sortOrder: 0 }] } };
    serve(realShape); mount(); await screen.findByRole("heading", { level: 1 });
    expect(screen.getByRole("link", { name: "rpg" })).toHaveAttribute("href", "/games?genre=rpg");
    expect(screen.getByRole("link", { name: "pc" })).toHaveAttribute("href", "/games?platform=pc");
    expect(screen.getAllByText("pc").length).toBeGreaterThan(0);
    // Kapak sayfanın üstünde gösterilir; yalnız kapaktan ibaret medya galeri bölümü açmaz.
    expect(screen.queryByRole("group", { name: tr.Game.gallery })).toBeNull();
    expect(screen.queryByRole("heading", { name: tr.Game.media })).toBeNull();
  });

  it("galeri yalnız ek görselleri içerir; kapak ve aynı adresli görsel yinelenmez", async () => {
    const withShot: Detail = { ...base, game: { ...base.game, coverUrl: "https://images.example/cover.jpg" }, metadata: { ...base.metadata, languageSupport: [], systemRequirements: [],
      media: [{ type: "COVER", url: "https://images.example/cover.jpg", provider: "STEAM", sortOrder: 0 },
        { type: "SCREENSHOT", url: "https://images.example/cover.jpg", provider: "STEAM", sortOrder: 1 },
        { type: "SCREENSHOT", url: "https://images.example/shot.jpg", provider: "STEAM", sortOrder: 2 }] } };
    serve(withShot); mount(); await screen.findByRole("heading", { level: 1 });
    const gallery = within(screen.getByRole("group", { name: tr.Game.gallery }));
    expect(gallery.getAllByRole("img")).toHaveLength(1);
    expect(gallery.getByRole("img").getAttribute("src")).toContain("shot.jpg");
  });

  it("açıklama dili arayüz dilinden farklıysa bunu belirtir ve metnin dilini işaretler; aynı dilde not göstermez", async () => {
    serve({ ...base, description: "An English summary.", descriptionLanguage: "en" }); const first = mount("tr"); await screen.findByRole("heading", { level: 1 });
    expect(screen.getByText(/yalnızca İngilizce olarak sağlanmıştır/)).toBeVisible();
    expect(screen.getByText("An English summary.").getAttribute("lang")).toBe("en");
    first.unmount();
    serve({ ...base, description: "An English summary.", descriptionLanguage: "en" }); mount("en"); await screen.findByRole("heading", { level: 1 });
    expect(screen.queryByText(/has not been translated/)).toBeNull();
    expect(screen.getByText("An English summary.").getAttribute("lang")).toBe("en");
  });

  it("açıklama dili bilinmiyorsa not göstermez ve dil uydurmaz", async () => {
    serve({ ...base, descriptionLanguage: null }); mount("tr"); await screen.findByRole("heading", { level: 1 });
    expect(screen.queryByText(/yalnızca .* olarak sağlanmıştır/)).toBeNull();
    expect(screen.getByText("Katalogdan alınan açıklama.").hasAttribute("lang")).toBe(false);
  });

  it("TR/EN geçişinde arayüz diline uygun açıklamayı ister ve gösterir; Türkçe varsa not yok, yoksa İngilizce ve not", async () => {
    const byLang = (resource: GameResource): Detail => {
      const lang = resource.kind === "detail" ? resource.lang : undefined;
      return lang === "tr" ? { ...base, description: "Türkçe mağaza açıklaması.", descriptionLanguage: "tr" } : { ...base, description: "English store description.", descriptionLanguage: "en" };
    };
    load.mockImplementation(async (resource: GameResource) => {
      if (resource.kind === "detail") return byLang(resource);
      if (resource.kind === "session") return null;
      if (resource.kind === "prices") return { market: { country: "US", currency: "USD" }, items: [{ gameId, status: "NO_OFFER", offers: [] }] };
      if (resource.kind === "module") return { kind: "achievements", items: [], totalElements: 0, page: 0, size: 20 };
      return undefined;
    });
    const turkish = mount("tr"); await screen.findByRole("heading", { level: 1 });
    expect(load.mock.calls.some(([resource]) => resource.kind === "detail" && resource.lang === "tr")).toBe(true);
    expect(screen.getByText("Türkçe mağaza açıklaması.").getAttribute("lang")).toBe("tr");
    expect(screen.queryByText(/yalnızca .* olarak sağlanmıştır/)).toBeNull();
    turkish.unmount();
    mount("en"); await screen.findByRole("heading", { level: 1 });
    expect(load.mock.calls.some(([resource]) => resource.kind === "detail" && resource.lang === "en")).toBe(true);
    expect(screen.getByText("English store description.").getAttribute("lang")).toBe("en");
  });

  it("Türkçe açıklama yokken TR arayüzde İngilizce yedek ve bilgilendirme gösterilir (İngilizce metin Türkçe diye işaretlenmez)", async () => {
    serve({ ...base, description: "English fallback text.", descriptionLanguage: "en" }); mount("tr"); await screen.findByRole("heading", { level: 1 });
    expect(screen.getByText(/yalnızca İngilizce olarak sağlanmıştır/)).toBeVisible();
    expect(screen.getByText("English fallback text.").getAttribute("lang")).toBe("en");
  });
});
