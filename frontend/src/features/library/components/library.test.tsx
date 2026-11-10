import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { ApiHttpError } from "@/shared/lib/api/client";
import { loadLibraryResource, mutateLibrary } from "../services/library-api";
import type { LibraryItem, LibraryPrivacy, LibraryResource } from "../types/library";
import { LibraryGameCard } from "./library-game-card";
import { LibraryOverview } from "./library-overview";
import { LibraryPrivacy as PrivacyScreen } from "./library-privacy";
import { LibraryProfileView } from "./library-profile-view";
import { LibraryStatus } from "./library-status";

const mocks = vi.hoisted(() => ({ status: "authenticated", accountId: "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66", query: "", push: vi.fn(), replace: vi.fn() }));
vi.mock("@/features/auth", () => ({ useRequireSession: () => ({ status: mocks.status, me: mocks.status === "authenticated" ? { accountId: mocks.accountId } : null }) }));
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(mocks.query), usePathname: () => "/tr/kutuphane" }));
vi.mock("@/shared/lib/i18n/navigation", () => ({
  useRouter: () => ({ push: mocks.push, replace: mocks.replace }),
  Link: ({ href, children, ...props }: { href: string | { pathname: string }; children: ReactNode }) => <a href={typeof href === "string" ? href : href.pathname} {...props}>{children}</a>
}));
vi.mock("../services/library-api", () => ({ libraryApi: {}, loadLibraryResource: vi.fn(), mutateLibrary: vi.fn() }));
vi.mock("next/image", () => ({ default: () => <span data-testid="cover-image" /> }));
const load = vi.mocked(loadLibraryResource);
const mutate = vi.mocked(mutateLibrary);
const item: LibraryItem = {
  entry: { id: "entry", gameId: "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66", inCollection: true, favorite: true, completed: false, addedAt: "2026-11-01T12:00:00Z", updatedAt: "2026-11-01T12:00:00Z", version: 0 },
  game: { gameId: "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66", title: "Test oyunu", coverUrl: null, origin: "INTERNAL", metadataCompleteness: "PARTIAL", gameType: "GAME", status: "AVAILABLE", metadataSource: "LIVE", metadataRefreshedAt: null }
};
const stats = { totalGames: 12, favoriteGames: 4, completedGames: 3 };
const settings: LibraryPrivacy = { version: 2, library: "PUBLIC", friendsList: "FRIENDS", hardware: "PRIVATE", performance: "PRIVATE", guestProfileAccess: "DETAILED" };
function wrap(children: ReactNode) { return <NextIntlClientProvider locale="tr" messages={tr}>{children}</NextIntlClientProvider>; }
beforeEach(() => {
  vi.clearAllMocks(); mocks.status = "authenticated"; mocks.query = ""; mocks.accountId = "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66";
  load.mockImplementation(async (resource: LibraryResource) => resource.kind === "statistics" ? stats : resource.kind === "privacy" ? settings : { items: [item], totalElements: 1, page: 0, size: 20, metadataPartial: false });
  mutate.mockResolvedValue(undefined);
});

describe("Library kartı", () => {
  it("gerçek bayrakları, tarihi ve eksik kapağı gösterir", () => {
    render(wrap(<LibraryGameCard item={item} />));
    expect(screen.getByRole("heading", { name: "Test oyunu" })).toBeVisible();
    expect(screen.getByTestId("cover-placeholder")).toBeVisible();
    expect(screen.getByRole("button", { name: "Favoriden çıkar" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByText(/Eklenme:/)).toBeVisible();
  });
  it("eksik isim uydurmaz; son bilinen bilgiyi tarih ve kesintiyle ayırır", () => {
    render(wrap(<LibraryGameCard item={{ ...item, game: { ...item.game, title: null, metadataSource: "LAST_KNOWN", status: "TEMPORARILY_UNAVAILABLE", metadataRefreshedAt: "2026-11-01T12:00:00Z" } }} />));
    expect(screen.getByRole("heading", { name: "Oyun adı mevcut değil" })).toBeVisible();
    expect(screen.getByText("Son bilinen bilgi")).toBeVisible();
    expect(screen.getByText(/geçici olarak doğrulanamıyor/)).toBeVisible();
    expect(screen.getByRole("button", { name: "Bitirildi işaretle" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Favoriden çıkar" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Kütüphaneden kaldır" })).toBeEnabled();
  });
  it("favori ve bitirildi eylemleri bağımsız komut gönderir", () => {
    const onAction = vi.fn();
    render(wrap(<LibraryGameCard item={item} onAction={onAction} />));
    fireEvent.click(screen.getByRole("button", { name: "Favoriden çıkar" }));
    expect(onAction).toHaveBeenLastCalledWith({ kind: "favorite", gameId: item.entry.gameId, enabled: false });
    fireEvent.click(screen.getByRole("button", { name: "Bitirildi işaretle" }));
    expect(onAction).toHaveBeenLastCalledWith({ kind: "completed", gameId: item.entry.gameId, enabled: true });
  });
  it("salt okunur kartta mutasyon düğmesi çizmez", () => {
    render(wrap(<LibraryGameCard item={item} readOnly />));
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});

describe("Library kişisel ekranı", () => {
  it("yükleme iskeleti ardından gerçek istatistik ve metadataPartial gösterir", async () => {
    load.mockImplementation(async (resource) => resource.kind === "statistics" ? stats : { items: [item], totalElements: 1, page: 0, size: 20, metadataPartial: true });
    render(wrap(<LibraryOverview />));
    expect(screen.getByRole("status", { name: "Kütüphane yükleniyor" })).toBeVisible();
    expect(await screen.findByText("12")).toBeVisible();
    expect(await screen.findByText(/Bazı oyun bilgileri eksik/)).toBeVisible();
  });
  it("boş koleksiyon ve boş son sayfa farklıdır", async () => {
    load.mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0, metadataPartial: false });
    const view = render(wrap(<LibraryOverview />));
    expect(await screen.findByText("Henüz oyun yok")).toBeVisible();
    mocks.query = "page=2"; view.rerender(wrap(<LibraryOverview />));
    expect(await screen.findByText(/Bu sayfada kayıt yok/)).toBeVisible();
    expect(screen.queryByText("Henüz oyun yok")).not.toBeInTheDocument();
  });
  it("silme açık onay olmadan API'ye gitmez; iptal ve Esc çalışır", async () => {
    render(wrap(<LibraryOverview />));
    fireEvent.click(await screen.findByRole("button", { name: "Kütüphaneden kaldır" }));
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText(/Favori ve bitirildi işaretleri de silinecek/)).toBeVisible();
    expect(mutate).not.toHaveBeenCalled();
    fireEvent(dialog, new Event("cancel", { bubbles: true, cancelable: true }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Kütüphaneden kaldır" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Kütüphaneden kaldır" }));
    await waitFor(() => expect(mutate).toHaveBeenCalledWith({ kind: "remove", gameId: item.entry.gameId }, expect.any(AbortSignal)));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });
  it("hızlı çift tıklama tek mutasyon ve sayaç/liste yeniden okuması üretir", async () => {
    let finish!: () => void;
    mutate.mockImplementation(() => new Promise((resolve) => { finish = () => resolve(undefined); }));
    render(wrap(<LibraryOverview />));
    const button = await screen.findByRole("button", { name: "Favoriden çıkar" });
    act(() => { fireEvent.click(button); fireEvent.click(button); });
    expect(mutate).toHaveBeenCalledTimes(1);
    expect(button).toBeDisabled();
    await act(async () => finish());
    await waitFor(() => expect(load.mock.calls.filter(([resource]) => resource.kind === "statistics")).toHaveLength(2));
  });
  it("çıkış ve hesap değişiminde eski veriyi hemen gizler", async () => {
    const view = render(wrap(<LibraryOverview />));
    await screen.findByRole("heading", { name: "Test oyunu" });
    load.mockImplementation(() => new Promise(() => {}));
    mocks.accountId = "other-account";
    view.rerender(wrap(<LibraryOverview />));
    expect(screen.queryByText("Test oyunu")).not.toBeInTheDocument();
    mocks.status = "guest";
    view.rerender(wrap(<LibraryOverview />));
    expect(screen.queryByText("12")).not.toBeInTheDocument();
  });
  it("oturumsuz ziyarette Library API çağrılmaz", () => {
    mocks.status = "guest";
    render(wrap(<LibraryOverview />));
    expect(load).not.toHaveBeenCalled();
  });
  it("API filtreleri URL'ye taşınır ve sayfa sıfırlanır", async () => {
    mocks.query = "page=3&completed=false";
    render(wrap(<LibraryOverview view="favorites" />));
    await screen.findByText("Test oyunu");
    expect(load).toHaveBeenCalledWith({ kind: "collection", query: expect.objectContaining({ favorite: true, completed: false, page: 3 }) }, expect.any(AbortSignal));
    fireEvent.click(screen.getByRole("combobox", { name: "Sıralama" }));
    fireEvent.click(screen.getByRole("option", { name: "Oyun adı: A → Z" }));
    expect(mocks.push).toHaveBeenCalledWith({ pathname: "/library/favorites", query: { completed: "false", sort: "TITLE", direction: "ASC" } }, { scroll: false });
  });
});

describe("Library gizlilik ve profil", () => {
  it("gizlilik sürüm çakışmasında hata kalır ve güncel tam nesne yeniden alınır", async () => {
    mutate.mockRejectedValue(new ApiHttpError(409, { code: "CONCURRENT_MODIFICATION" }));
    render(wrap(<PrivacyScreen />));
    fireEvent.click(await screen.findByRole("radio", { name: /Yalnız ben/ }));
    fireEvent.click(screen.getByRole("button", { name: "Kaydet" }));
    await waitFor(() => expect(mutate).toHaveBeenCalledWith({ kind: "privacy", settings, library: "PRIVATE" }, expect.any(AbortSignal)));
    expect(await screen.findByRole("alert")).toHaveTextContent("Ayarlar başka bir yerde değişti");
    await waitFor(() => expect(load).toHaveBeenCalledTimes(2));
    expect(await screen.findByRole("radio", { name: /Herkese açık/ })).toBeChecked();
  });
  it.each([403, 503])("profil %s: kartlar ve sayaçlar gösterilmez", async (status) => {
    load.mockRejectedValue(new ApiHttpError(status));
    render(wrap(<LibraryProfileView userId={item.entry.gameId} />));
    expect(await screen.findByRole("alert")).toHaveTextContent(status === 403 ? "görüntüleme iznin yok" : "görünürlüğü şu anda doğrulanamıyor");
    expect(screen.queryByText("Test oyunu")).not.toBeInTheDocument();
    expect(screen.queryByText("Toplam oyun")).not.toBeInTheDocument();
  });
  it("izinli profil salt okunurdur", async () => {
    load.mockResolvedValue({ collection: { items: [item], page: 0, size: 20, totalElements: 1 }, statistics: stats });
    render(wrap(<LibraryProfileView userId={item.entry.gameId} />));
    expect(await screen.findByText("Test oyunu")).toBeVisible();
    expect(screen.queryByRole("button", { name: /Favoriden|Bitirildi|Kütüphaneden/ })).not.toBeInTheDocument();
  });
  it.each([400, 401, 403, 404, 409, 429, 500, 503])("%s hata metni güvenli ve yerelleştirilmiştir", (status) => {
    render(wrap(<LibraryStatus error={new ApiHttpError(status)} />));
    expect(screen.getByRole("alert")).toHaveTextContent(tr.Library.errors[String(status) as keyof typeof tr.Library.errors]);
  });
});
