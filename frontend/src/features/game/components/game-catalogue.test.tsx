import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import messages from "@/shared/lib/i18n/messages/tr.json";
import { ApiHttpError } from "@/shared/lib/api/client";
import { loadGameResource } from "../services/game-api";
import type { DiscoveryPage, GameResource } from "../types/game";
import { GameCatalogue } from "./game-catalogue";

const replace = vi.fn();
const push = vi.fn();
let search = "";
type SessionStub = { status: string; me: { adultVerified?: boolean } | null };
const session = vi.hoisted(() => ({ value: { status: "guest", me: null } as SessionStub }));
vi.mock("@/features/auth", () => ({ useSession: () => session.value }));
vi.mock("@/shared/lib/i18n/navigation", () => ({
  Link: ({ href, children, ...rest }: { href: { pathname?: string; params?: { gameId: string }; query?: Record<string, string> } | string; children: React.ReactNode } & Record<string, unknown>) => <a {...rest} href={typeof href === "string" ? href : href.params ? `/games/${href.params.gameId}` : `${href.pathname}${href.query ? "?" + new URLSearchParams(href.query).toString() : ""}`}>{children}</a>,
  useRouter: () => ({ replace, push })
}));
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(search), usePathname: () => "/tr/oyunlar", useParams: () => ({}), useRouter: () => ({ replace, push }) }));
vi.mock("../services/game-api", async (original) => ({ ...await original<typeof import("../services/game-api")>(), loadGameResource: vi.fn(), mutateGame: vi.fn() }));
const load = vi.mocked(loadGameResource);
const ids = ["0198a3c2-0001-7d44-9a55-3f1c2b4d5e66", "0198a3c2-0002-7d44-9a55-3f1c2b4d5e66", "0198a3c2-0003-7d44-9a55-3f1c2b4d5e66"];
const card = (index: number, extra: object = {}) => ({ gameId: ids[index], title: `Fixture game ${index}`, origin: "INTERNAL" as const, metadataCompleteness: "PARTIAL", coverUrl: null, gameType: "MAIN_GAME", ...extra });
const page = (items: DiscoveryPage["items"], totalElements = items.length): DiscoveryPage => ({ items, page: 0, size: 20, totalElements });
const three = page([
  { game: card(0), releaseDate: "2026-01-01", rating: { status: "RATED", averageRating: 8.5, ratingCount: 12 } },
  { game: card(1), releaseDate: null, rating: { status: "UNRATED", averageRating: null, ratingCount: 0 } },
  { game: card(2, { coverUrl: "https://images.example/cover.jpg" }), releaseDate: "2026-02-01", earlyAccess: true, rating: { status: "UNAVAILABLE", averageRating: null, ratingCount: null } }
]);
function serve(handlers: Partial<Record<GameResource["kind"], (resource: GameResource) => unknown>> = {}) {
  load.mockImplementation(async (resource) => {
    const handler = handlers[resource.kind];
    if (handler) return handler(resource);
    if (resource.kind === "session") return null;
    if (resource.kind === "discovery") return three;
    if (resource.kind === "popular") return page([{ game: card(0), releaseDate: null, editorialRank: 1 }]);
    return undefined;
  });
}
function mount() { return render(<NextIntlClientProvider locale="tr" messages={messages}><GameCatalogue /></NextIntlClientProvider>); }
const discoveryCalls = () => load.mock.calls.map(([resource]) => resource).filter((resource) => resource.kind === "discovery").map((resource) => new URLSearchParams((resource as { query: string }).query));
beforeEach(() => { search = ""; session.value = { status: "guest", me: null }; serve(); });
afterEach(() => {
  // Bekleyen React efektlerini mock uygulamaları hâlâ geçerliyken temizle.
  cleanup();
  vi.resetAllMocks();
  replace.mockReset();
  push.mockReset();
});

describe("Oyun kataloğu", () => {
  it("kart olarak listeler; kapaksız kayıtta yer tutucu kullanır ve puan durumlarını ayırır", async () => {
    mount();
    const list = (await screen.findByRole("link", { name: "Fixture game 0" })).closest("ul")!;
    expect(within(list).getAllByRole("listitem")).toHaveLength(3);
    expect(within(list).getAllByTestId("cover-placeholder")).toHaveLength(2);
    expect(within(list).getByText("8,5")).toBeVisible();
    expect(within(list).getByText(messages.Game.unrated)).toBeVisible();
    expect(within(list).getByText(messages.Game.ratingUnavailable)).toBeVisible();
    const mark = within(list).getByTestId("early-access-mark");
    expect(mark).toHaveTextContent(messages.Game.earlyAccessBadge);
    // Rozetler kapağın üstünde, tek bir konumlu kapsayıcıdadır (üst üste binmez, birden çok rozet alt alta dizilir).
    const marks = mark.parentElement!;
    expect(marks).toHaveAttribute("data-testid", "status-marks");
    expect(marks.className).toMatch(/absolute/);
    expect(marks.className).toMatch(/top-2/);
    expect(within(list).getAllByTestId("early-access-mark")).toHaveLength(1);
    expect(within(list).queryByTestId("free-mark")).toBeNull();
    expect(within(list).queryByTestId("coming-soon-mark")).toBeNull();
    expect(within(list).getAllByText(messages.Game.releaseUnknown)).toHaveLength(1);
    expect(within(list).queryByText(/^0([,/]|$)/)).toBeNull();
    expect(screen.getByRole("link", { name: "Fixture game 1" })).toHaveAttribute("href", `/games/${ids[1]}`);
    expect(screen.getByRole("status")).toHaveTextContent(messages.Game.total.replace("{count}", "3"));
  });
  it("ücretsiz ve henüz çıkmamış oyunları kapakta rozetle işaretler; bilinmeyen (null) durumda rozet göstermez", async () => {
    serve({ discovery: () => page([
      { game: card(0), releaseDate: null, free: true },
      { game: card(1), releaseDate: null, comingSoon: true },
      { game: card(2), releaseDate: null, earlyAccess: true, free: true, comingSoon: true },
      { game: card(0, { gameId: "0198a3c2-0004-7d44-9a55-3f1c2b4d5e66", title: "Fixture game unknown" }), releaseDate: null, free: null, comingSoon: null }
    ]) });
    mount();
    const list = (await screen.findByRole("link", { name: "Fixture game 0" })).closest("ul")!;
    expect(within(list).getAllByTestId("free-mark")).toHaveLength(2);
    expect(within(list).getAllByTestId("free-mark")[0]).toHaveTextContent(messages.Game.freeBadge);
    expect(within(list).getAllByTestId("coming-soon-mark")).toHaveLength(2);
    expect(within(list).getAllByTestId("coming-soon-mark")[0]).toHaveTextContent(messages.Game.comingSoonBadge);
    expect(within(list).getAllByTestId("early-access-mark")).toHaveLength(1);
    // Üç durumun hepsi olan kartta rozetler tek kapsayıcıda alt alta durur.
    const all = within(list).getByTestId("early-access-mark").parentElement!;
    expect(all.children).toHaveLength(3);
    // Durumu bilinmeyen kartta hiç rozet kapsayıcısı yoktur.
    const unknown = screen.getByRole("link", { name: "Fixture game unknown" }).closest("li")!;
    expect(within(unknown).queryByTestId("status-marks")).toBeNull();
  });
  it("yetişkin içerikli oyunu kapakta +18 rozetiyle işaretler; false ve bilinmeyen (null) durumda rozet göstermez", async () => {
    serve({ discovery: () => page([
      { game: card(0), releaseDate: null, adult: true },
      { game: card(1), releaseDate: null, adult: false },
      { game: card(2), releaseDate: null, adult: null }
    ]) });
    mount();
    const list = (await screen.findByRole("link", { name: "Fixture game 0" })).closest("ul")!;
    expect(within(list).getAllByTestId("adult-mark")).toHaveLength(1);
    expect(within(list).getByTestId("adult-mark")).toHaveTextContent(messages.Game.adultBadge);
    expect(within(screen.getByRole("link", { name: "Fixture game 1" }).closest("li")!).queryByTestId("adult-mark")).toBeNull();
    expect(within(screen.getByRole("link", { name: "Fixture game 2" }).closest("li")!).queryByTestId("status-marks")).toBeNull();
  });
  it("kart başına ek API çağrısı yapmaz", async () => {
    mount(); await screen.findByRole("link", { name: "Fixture game 0" });
    expect(load.mock.calls.map(([resource]) => resource.kind).filter((kind) => kind === "detail" || kind === "cards")).toEqual([]);
  });
  it("yüklenirken iskelet gösterir ve eski sonucu göstermez", async () => {
    let finish: (value: unknown) => void = () => {};
    serve({ discovery: () => new Promise((resolve) => { finish = resolve; }) });
    const { container } = mount();
    expect(container.querySelector("ul[aria-hidden=true]")).not.toBeNull();
    expect(screen.queryByRole("link", { name: "Fixture game 0" })).toBeNull();
    finish(three);
    await screen.findByRole("link", { name: "Fixture game 0" });
    expect(container.querySelector("ul[aria-hidden=true]")).toBeNull();
  });
  it("grid ve liste görünümünü URL'ye yazar", async () => {
    mount(); await screen.findByRole("link", { name: "Fixture game 0" });
    expect(screen.getByRole("button", { name: messages.Game.viewGrid })).toHaveAttribute("aria-pressed", "true");
    fireEvent.click(screen.getByRole("button", { name: messages.Game.viewList }));
    expect(push).toHaveBeenCalledWith({ pathname: "/games", query: { view: "list" } }, { scroll: false });
  });
  it("aktif filtreleri chip olarak gösterir; tek filtre kaldırılır ve sayfa sıfırlanır", async () => {
    search = "genre=rpg&minRating=7&page=2&popularOnly=true";
    mount(); await screen.findByRole("link", { name: "Fixture game 0" });
    const chips = within(screen.getByRole("group", { name: messages.Game.activeFilters }));
    expect(chips.getByRole("button", { name: /rpg/ })).toBeVisible();
    fireEvent.click(chips.getByRole("button", { name: /rpg/ }));
    expect(push).toHaveBeenCalledWith({ pathname: "/games", query: { minRating: "7", popularOnly: "true" } }, { scroll: false });
  });
  it("tüm filtreleri tek hamlede temizler", async () => {
    search = "genre=rpg&platform=pc&sort=RATING";
    mount(); await screen.findByRole("link", { name: "Fixture game 0" });
    fireEvent.click(within(screen.getByRole("group", { name: messages.Game.activeFilters })).getByRole("button", { name: messages.Game.clearFilters }));
    expect(push).toHaveBeenCalledWith({ pathname: "/games", query: { sort: "RATING" } }, { scroll: false });
  });
  it("filtreyi tuş başına değil Uygula ile gönderir; çoklu filtre tek geçişte yazılır", async () => {
    mount(); await screen.findByRole("link", { name: "Fixture game 0" });
    fireEvent.click(screen.getByRole("button", { name: messages.Game.filters }));
    fireEvent.change(screen.getByLabelText(messages.Game.fields.genre), { target: { value: "rpg" } });
    fireEvent.change(screen.getByLabelText(messages.Game.fields.platform), { target: { value: "pc" } });
    expect(push).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: messages.Game.applyFilters }));
    expect(push).toHaveBeenCalledTimes(1);
    const query = (push.mock.calls[0][0] as { query: Record<string, string> }).query;
    expect(query).toMatchObject({ genre: "rpg", platform: "pc" });
  });
  it("ters tarih aralığı ve geçersiz puanı Uygula'dan önce engeller", async () => {
    mount(); await screen.findByRole("link", { name: "Fixture game 0" });
    fireEvent.click(screen.getByRole("button", { name: messages.Game.filters }));
    fireEvent.change(screen.getByLabelText(messages.Game.fields.releasedAfter), { target: { value: "2026-02-01" } });
    fireEvent.change(screen.getByLabelText(messages.Game.fields.releasedBefore), { target: { value: "2026-01-01" } });
    expect(screen.getByRole("alert")).toHaveTextContent(messages.Game.filterErrors.dateRange);
    expect(screen.getByRole("button", { name: messages.Game.applyFilters })).toBeDisabled();
    fireEvent.change(screen.getByLabelText(messages.Game.fields.releasedBefore), { target: { value: "" } });
    fireEvent.change(screen.getByLabelText(messages.Game.fields.minRating), { target: { value: "11" } });
    expect(screen.getByRole("alert")).toHaveTextContent(messages.Game.filterErrors.minRating);
    expect(screen.getByRole("button", { name: messages.Game.applyFilters })).toBeDisabled();
  });
  it("URL'deki geçersiz filtreyi sunucuya göndermez ve kullanıcıya bildirir", async () => {
    search = "releasedAfter=2026-02-01&releasedBefore=2026-01-01&minRating=0.5&genre=rpg";
    mount(); await screen.findByRole("link", { name: "Fixture game 0" });
    const query = discoveryCalls()[0];
    expect(query.get("genre")).toBe("rpg");
    expect(["releasedAfter", "releasedBefore", "minRating"].filter((key) => query.has(key))).toEqual([]);
    expect(screen.getAllByRole("alert").some((node) => node.textContent?.includes(messages.Game.filterNotSent))).toBe(true);
  });
  it("sıralamayı ve yönü sorguya taşır", async () => {
    search = "sort=RATING&direction=DESC";
    mount(); await screen.findByRole("link", { name: "Fixture game 0" });
    expect(discoveryCalls()[0].get("sort")).toBe("RATING");
    expect(discoveryCalls()[0].get("direction")).toBe("DESC");
    expect(screen.getByLabelText(messages.Game.sort)).toHaveValue("RATING");
  });
  it("filtre sonucu boşsa filtre mesajı ve temizleme sunar; katalog durumu sorulmaz", async () => {
    search = "genre=none";
    serve({ discovery: () => page([]) });
    mount();
    await screen.findByText(messages.Game.emptyFilters);
    fireEvent.click(screen.getAllByRole("button", { name: messages.Game.clearFilters }).at(-1)!);
    expect(push).toHaveBeenCalled();
  });
  it("yalnız arama boşsa aranan ifadeyle arama mesajı ve filtre temizleme gösterir; herkese açık indeks bağlantısı yoktur", async () => {
    search = "q=zzz";
    serve({ discovery: () => page([]) });
    mount();
    await screen.findByText(messages.Game.emptySearch.replace("{query}", "zzz"));
    expect(screen.queryByRole("button", { name: messages.Game.indexSearch })).toBeNull();
    expect(screen.queryByRole("link", { name: messages.Game.indexSearch })).toBeNull();
    fireEvent.click(screen.getAllByRole("button", { name: messages.Game.clearFilters }).at(-1)!);
    expect(push).toHaveBeenCalled();
  });
  const status = (extra: object) => () => ({ provider: "STEAM", runMode: "INITIAL", syncStatus: "COMPLETE", cursor: 1, processedCount: 0, indexedEntries: 0, mainGameEntries: 0, unclassifiedEntries: 0, providerTotal: null, runStartedAt: null, lastSuccessfulAt: null, lastErrorCode: null, failureCount: 0, nextAttemptAt: null, ...extra });
  it("filtresiz boş katalog + senkron sürüyor: sürüyor ipucu ve sayılar", async () => {
    serve({ discovery: () => page([]), catalogStatus: status({ syncStatus: "RUNNING", indexedEntries: 1200, mainGameEntries: 3 }) });
    mount();
    await screen.findByText(/Steam kataloğu şu an senkronize ediliyor/);
    expect(screen.getByText(/1\.200 kayıt indekslendi, 3 oyun/)).toBeVisible();
  });
  it("filtresiz boş katalog + indeks dolu ama sınıflandırılmamış: sınıflandırma bekleniyor mesajı", async () => {
    serve({ discovery: () => page([]), catalogStatus: status({ indexedEntries: 50, unclassifiedEntries: 50 }) });
    mount();
    await screen.findByText(messages.Game.emptyUnclassifiedHint.replace("{indexed}", "50"));
  });
  it("filtresiz boş katalog + indeks de boş: katalog tamamen boş mesajı", async () => {
    serve({ discovery: () => page([]), catalogStatus: status({}) });
    mount();
    await screen.findByText(messages.Game.emptyCatalogueAll);
  });
  it("katalog durumu alınamazsa genel boş mesajına düşer (uydurma durum yok)", async () => {
    serve({ discovery: () => page([]), catalogStatus: () => { throw new ApiHttpError(503); } });
    mount();
    await screen.findByText(messages.Game.emptyCatalogue);
  });
  it("keşif API'si hata verirse boş durum değil hata gösterilir", async () => {
    serve({ discovery: () => { throw new ApiHttpError(503); } });
    mount();
    await screen.findByRole("alert");
    expect(screen.queryByText(messages.Game.emptyCatalogueAll)).toBeNull();
  });
  it("son sayfanın ötesindeki sayfada boş ekran yerine son sayfaya götürür", async () => {
    search = "page=9";
    serve({ discovery: () => ({ ...page([], 45), page: 9 }) });
    mount();
    fireEvent.click(await screen.findByRole("button", { name: messages.Game.lastPage }));
    expect(push).toHaveBeenCalledWith({ pathname: "/games", query: { page: "2" } }, { scroll: false });
  });
  it("sayfalamada sonraki sayfayı URL'ye yazar", async () => {
    serve({ discovery: () => page(three.items, 45) });
    mount(); await screen.findByRole("link", { name: "Fixture game 0" });
    fireEvent.click(screen.getByRole("button", { name: messages.Game.next }));
    expect(push).toHaveBeenCalledWith({ pathname: "/games", query: { page: "1" } }, { scroll: false });
  });
  it("API hatasında anlaşılır mesaj ve yeniden deneme sunar", async () => {
    serve({ discovery: () => { throw new ApiHttpError(503); } });
    mount();
    expect(await screen.findByText(messages.Game.errors["503"])).toBeVisible();
    expect(screen.getByRole("button", { name: messages.Game.retry })).toBeVisible();
  });
  it("puan altyapısı hazır değilse RATING_INTEGRATION_PENDING mesajını gösterir", async () => {
    serve({ discovery: () => { throw new ApiHttpError(400, { code: "RATING_INTEGRATION_PENDING" }); } });
    mount();
    expect(await screen.findByText(messages.Game.ratingPending)).toBeVisible();
  });
  it("Editörün Seçtikleri sekmesi popular ucunu kullanır ve trend iddiası taşımaz", async () => {
    search = "tab=picks";
    mount();
    expect(await screen.findByText(messages.Game.editorsPicksHelp)).toBeVisible();
    await screen.findByRole("link", { name: "Fixture game 0" });
    expect(load.mock.calls.some(([resource]) => resource.kind === "popular")).toBe(true);
    expect(discoveryCalls()).toHaveLength(0);
    expect(screen.getByText(messages.Game.editorRank.replace("{rank}", "1"))).toBeVisible();
  });
  it("boş editör listesinde sahte oyun eklemez", async () => {
    search = "tab=picks";
    serve({ popular: () => page([]) });
    mount();
    expect(await screen.findByText(messages.Game.emptyPicks)).toBeVisible();
    expect(screen.queryByRole("link", { name: /Fixture/ })).toBeNull();
  });
  it("kaldırılan geri bildirim sekmesi adresi tüm oyunlar listesine döner ve kişisel istek atmaz", async () => {
    search = "tab=feedback";
    mount();
    expect(await screen.findByRole("link", { name: "Fixture game 0" })).toBeVisible();
    expect(within(screen.getByRole("navigation", { name: messages.Game.catalogue })).getByRole("link", { name: messages.Game.allGames })).toHaveAttribute("aria-current", "page");
    expect(load.mock.calls.some(([resource]) => resource.kind === "myFeedback")).toBe(false);
  });
  it("bölüm gezintisi: Tüm oyunlar etkin; Editörün Seçtikleri bağlantısı; misafire kişisel/yönetim bölümü gösterilmez", async () => {
    mount(); await screen.findByRole("link", { name: "Fixture game 0" });
    const nav = within(screen.getByRole("navigation", { name: messages.Game.catalogue }));
    expect(nav.getByRole("link", { name: messages.Game.allGames })).toHaveAttribute("aria-current", "page");
    expect(nav.getByRole("link", { name: messages.Game.editorsPicks })).toHaveAttribute("href", "/games?tab=picks");
    expect(nav.queryByRole("link", { name: messages.Game.myFeedback })).toBeNull();
    expect(nav.queryByRole("link", { name: messages.Game.workspace.admin })).toBeNull();
  });
  it("Editörün Seçtikleri sekmesinde bölüm gezintisinde o bağlantı etkindir", async () => {
    search = "tab=picks";
    mount(); await screen.findByRole("link", { name: "Fixture game 0" });
    expect(within(screen.getByRole("navigation", { name: messages.Game.catalogue })).getByRole("link", { name: messages.Game.editorsPicks })).toHaveAttribute("aria-current", "page");
  });
  it("katalog durumunu yalnız açıldığında ister; providerTotal null iken yüzde göstermez", async () => {
    serve({ catalogStatus: () => ({ provider: "STEAM", runMode: "INITIAL", syncStatus: "FAILED", cursor: 440, processedCount: 10, indexedEntries: 10, mainGameEntries: 0, unclassifiedEntries: 10, providerTotal: null, runStartedAt: null, lastSuccessfulAt: null, lastErrorCode: "SYNC_ERROR", failureCount: 3, nextAttemptAt: null }) });
    mount(); await screen.findByRole("link", { name: "Fixture game 0" });
    expect(load.mock.calls.some(([resource]) => resource.kind === "catalogStatus")).toBe(false);
    fireEvent.click(screen.getByRole("button", { name: messages.Game.catalogStatus }));
    expect(await screen.findByText(messages.Game.providerTotalUnknown)).toBeVisible();
    expect(screen.getByText(messages.Game.autoRetryStopped)).toBeVisible();
    expect(screen.getByText("SYNC_ERROR")).toBeVisible();
    expect(screen.queryByText(/%\s?\d/)).toBeNull();
    await waitFor(() => expect(load.mock.calls.filter(([resource]) => resource.kind === "catalogStatus")).toHaveLength(1));
  });
});

describe("Oyun kataloğu: +18 kapısı", () => {
  const verified: SessionStub = { status: "authenticated", me: { adultVerified: true } };
  const settings = () => screen.queryByRole("link", { name: messages.Game.adultVerifyLink });
  const note = () => screen.queryByText(messages.Game.adultNotApplied, { exact: false });
  it("yaşı doğrulanmış kullanıcıda includeAdult sunucuya gider; sunucu uyguladıysa ipucu çıkmaz", async () => {
    search = "includeAdult=true";
    session.value = verified;
    serve({ discovery: () => ({ ...three, includeAdultApplied: true }) });
    mount();
    await screen.findByRole("link", { name: "Fixture game 0" });
    expect(discoveryCalls().at(-1)!.get("includeAdult")).toBe("true");
    expect(note()).toBeNull();
  });
  it("sunucu includeAdultApplied=false derse (doğrulama geri alındı, iddia gecikmeli) yerel durum doğrulanmış olsa da ipucu gösterilir", async () => {
    search = "includeAdult=true";
    session.value = verified;
    serve({ discovery: () => ({ ...three, includeAdultApplied: false }) });
    mount();
    await screen.findByRole("link", { name: "Fixture game 0" });
    expect(note()).toBeVisible();
    expect(settings()).toHaveAttribute("href", "/profile/settings");
  });
  it("misafir ve doğrulanmamış kullanıcıda includeAdult sunucuya gönderilmez, URL'deki seçim korunur ve ipucu çıkar", async () => {
    for (const unverified of [{ status: "guest", me: null }, { status: "authenticated", me: { adultVerified: false } }]) {
      search = "includeAdult=true";
      session.value = unverified;
      mount();
      await screen.findByRole("link", { name: "Fixture game 0" });
      expect(discoveryCalls().every((query) => !query.has("includeAdult"))).toBe(true);
      expect(note()).toBeVisible();
      expect(settings()).toHaveAttribute("href", "/profile/settings");
      cleanup();
      load.mockClear();
    }
  });
  it("includeAdult istenmediyse hiçbir durumda +18 ipucu gösterilmez (eski sunucuda includeAdultApplied alanı yoksa da)", async () => {
    for (const current of [{ status: "guest", me: null }, verified]) {
      search = "";
      session.value = current;
      mount();
      await screen.findByRole("link", { name: "Fixture game 0" });
      expect(note()).toBeNull();
      cleanup();
    }
    search = "includeAdult=true";
    session.value = verified;
    mount();
    await screen.findByRole("link", { name: "Fixture game 0" });
    expect(note()).toBeNull();
  });
});
