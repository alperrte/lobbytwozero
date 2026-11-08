import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import messages from "@/shared/lib/i18n/messages/tr.json";
import { ApiHttpError } from "@/shared/lib/api/client";
import { loadGameResource, mutateGame } from "../services/game-api";
import type { DiscoveryPage, GameResource } from "../types/game";
import { GameAdminTools } from "./game-admin-tools";
import { GameIdentityNotice } from "./game-identity-notice";
import { GameModeration } from "./game-moderation";
import { GamePopularEditor, moveItem } from "./game-popular-editor";

vi.mock("@/shared/lib/i18n/navigation", () => ({ Link: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={String(href)}>{children}</a> }));
vi.mock("../services/game-api", async (original) => ({ ...await original<typeof import("../services/game-api")>(), loadGameResource: vi.fn(), mutateGame: vi.fn() }));
const load = vi.mocked(loadGameResource);
const mutate = vi.mocked(mutateGame);
const t = messages.Game;
const gid = (n: number) => `0198a3c2-00${String(n).padStart(2, "0")}-7d44-9a55-3f1c2b4d5e66`;
const card = (n: number, title = `Fixture ${n}`) => ({ gameId: gid(n), title, origin: "INTERNAL" as const, metadataCompleteness: "PARTIAL", coverUrl: null, gameType: "MAIN_GAME" });
const picks = (...numbers: number[]): DiscoveryPage => ({ items: numbers.map((n, index) => ({ game: card(n), releaseDate: null, editorialRank: index + 1 })), page: 0, size: 100, totalElements: numbers.length });
function serve(handlers: Partial<Record<GameResource["kind"], (resource: GameResource) => unknown>>) {
  load.mockImplementation(async (resource) => { const handler = handlers[resource.kind]; return handler ? handler(resource) : undefined; });
}
function wrap(node: React.ReactNode) { return render(<NextIntlClientProvider locale="tr" messages={messages}>{node}</NextIntlClientProvider>); }
beforeEach(() => { mutate.mockResolvedValue(undefined); });
afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe("Editör listesi sıralama yardımcısı", () => {
  it("komşu öğelerle yer değiştirir; sınır dışı hareket listeyi bozmaz", () => {
    expect(moveItem(["a", "b", "c"], 1, -1)).toEqual(["b", "a", "c"]);
    expect(moveItem(["a", "b", "c"], 1, 1)).toEqual(["a", "c", "b"]);
    expect(moveItem(["a", "b"], 0, -1)).toEqual(["a", "b"]);
    expect(moveItem(["a", "b"], 1, 1)).toEqual(["a", "b"]);
  });
});

describe("Editör (popüler) listesi yönetimi", () => {
  it("mevcut sırayı herkese açık uçtan okur; sıralama/çıkarma sonrası tek PUT ile gerekçeyle kaydeder", async () => {
    serve({ popular: () => picks(1, 2, 3) });
    wrap(<GamePopularEditor />);
    await screen.findByText("Fixture 1");
    expect(load.mock.calls[0][0]).toEqual({ kind: "popular", page: 0, size: 100 });
    fireEvent.click(screen.getByRole("button", { name: t.moveDown.replace("{title}", "Fixture 1") }));
    fireEvent.click(screen.getByRole("button", { name: t.removeFromList.replace("{title}", "Fixture 3") }));
    expect(screen.getByText(t.unsavedChanges, { selector: "span" })).toBeVisible();
    const form = screen.getByRole("form", { name: t.popularSave });
    fireEvent.change(within(form).getByLabelText(t.fields.reason), { target: { value: "Editör sırası güncellendi" } });
    expect(within(form).getByRole("button", { name: t.save })).toBeDisabled();
    fireEvent.click(within(form).getByLabelText(t.confirmAction));
    fireEvent.click(within(form).getByRole("button", { name: t.save }));
    await waitFor(() => expect(mutate).toHaveBeenCalledTimes(1));
    expect(mutate).toHaveBeenCalledWith({ operation: "popular", body: { gameIds: [gid(2), gid(1)], reason: "Editör sırası güncellendi" } });
  });
  it("boş liste kaydının mevcut seçimi temizleyeceğini açıkça söyler", async () => {
    serve({ popular: () => picks(1) });
    wrap(<GamePopularEditor />);
    await screen.findByText("Fixture 1");
    fireEvent.click(screen.getByRole("button", { name: t.removeFromList.replace("{title}", "Fixture 1") }));
    expect(screen.getByText(t.popularClearWarning)).toBeVisible();
    expect(screen.getByText(t.popularReplaceWarning)).toBeVisible();
    expect(screen.getByText(t.popularEmptyList)).toBeVisible();
  });
  it("oyun aramasından ekler; zaten listedekini önermez", async () => {
    serve({ popular: () => picks(1), discovery: () => picks(1, 4) });
    wrap(<GamePopularEditor />);
    await screen.findByText("Fixture 1");
    fireEvent.change(screen.getByRole("searchbox", { name: t.popularAdd }), { target: { value: "Fix" } });
    const result = await screen.findByRole("button", { name: new RegExp(String.raw`^Fixture 4\s*${t.pickerSelect}$`) }, { timeout: 2000 });
    expect(screen.queryByRole("button", { name: new RegExp(String.raw`^Fixture 1\s*${t.pickerSelect}$`) })).toBeNull();
    fireEvent.click(result);
    expect(await screen.findByText("Fixture 4")).toBeVisible();
    expect(screen.getByText("2/100")).toBeVisible();
  });
  it("mevcut liste okunamazsa sahte seçim göstermez", async () => {
    serve({ popular: () => { throw new ApiHttpError(503); } });
    wrap(<GamePopularEditor />);
    expect(await screen.findByText(t.errors["503"])).toBeVisible();
    expect(screen.queryByRole("form", { name: t.popularSave })).toBeNull();
  });
});

describe("Moderasyon", () => {
  const mediaId = "0198a3c2-9999-7d44-9a55-3f1c2b4d5e66";
  function open() {
    serve({ discovery: () => picks(1), publicDeveloper: () => ({ gameId: gid(1), description: { text: "Geliştirici açıklaması", updatedAt: "2026-01-01T00:00:00Z" }, media: [{ id: mediaId, type: "IMAGE", url: "https://images.example/a.png", altText: "Ekran görüntüsü", createdAt: "2026-01-01T00:00:00Z" }], updateNotes: [] }) });
    wrap(<GameModeration />);
  }
  async function pickGame() {
    fireEvent.change(screen.getByLabelText(t.pickerLabel), { target: { value: "Fix" } });
    fireEvent.click(await screen.findByRole("button", { name: /Fixture 1/ }, { timeout: 2000 }));
    await screen.findByText(t.moderationVisibleHelp);
  }
  it("açıklama gizlemede targetId göndermez", async () => {
    open(); await pickGame();
    const form = screen.getByRole("form", { name: t.moderateContent });
    fireEvent.change(within(form).getByLabelText(t.fields.reason), { target: { value: "Uygunsuz içerik" } });
    fireEvent.click(within(form).getByLabelText(t.confirmAction));
    fireEvent.click(within(form).getByRole("button", { name: t.save }));
    await waitFor(() => expect(mutate).toHaveBeenCalledTimes(1));
    const body = mutate.mock.calls[0][0].body as Record<string, unknown>;
    expect(body).toEqual({ target: "DESCRIPTION", action: "HIDE", reason: "Uygunsuz içerik" });
    expect("targetId" in body).toBe(false);
  });
  it("görünür medya seçildiğinde hedef türünü ve kimliğini forma taşır", async () => {
    open(); await pickGame();
    fireEvent.click(screen.getAllByRole("button", { name: t.pickerSelect })[1]);
    const form = screen.getByRole("form", { name: t.moderateContent });
    expect(within(form).getByLabelText(t.fields.target)).toHaveValue("MEDIA");
    expect(within(form).getByLabelText(t.fields.targetId)).toHaveValue(mediaId);
    fireEvent.change(within(form).getByLabelText(t.fields.action), { target: { value: "RESTORE" } });
    fireEvent.change(within(form).getByLabelText(t.fields.reason), { target: { value: "Yanlış gizleme" } });
    fireEvent.click(within(form).getByLabelText(t.confirmAction));
    fireEvent.click(within(form).getByRole("button", { name: t.save }));
    await waitFor(() => expect(mutate).toHaveBeenCalledTimes(1));
    expect(mutate.mock.calls[0][0]).toEqual({ operation: "moderate", gameId: gid(1), body: { target: "MEDIA", targetId: mediaId, action: "RESTORE", reason: "Yanlış gizleme" } });
  });
  it("sahte moderasyon kuyruğu üretmez; gizli içerik kimlikle geri alınır", async () => {
    open();
    expect(screen.queryByRole("table")).toBeNull();
    expect(screen.getByText(t.moderationScope)).toBeVisible();
  });
});

describe("Dış kimlik bakım alanı", () => {
  it("gerçek taşıma/ayırma için çalışan form veya düğme sunmaz", () => {
    wrap(<GameIdentityNotice />);
    expect(screen.getByText(t.identityBlockedBody)).toBeVisible();
    expect(screen.queryByRole("form")).toBeNull();
    expect(screen.queryByRole("button")).toBeNull();
  });
  it("sunucu FOREIGN_REFERENCE_SAFETY_UNVERIFIED dönerse güvenlik açıklamasını gösterir", async () => {
    mutate.mockRejectedValueOnce(new ApiHttpError(409, { code: "FOREIGN_REFERENCE_SAFETY_UNVERIFIED" }));
    serve({ session: () => null });
    const { GameActionForm } = await import("./game-action-form");
    wrap(<GameActionForm title="Fixture" fields={[]} build={() => ({ operation: "create" })} />);
    fireEvent.submit(screen.getByRole("form", { name: "Fixture" }));
    expect(await screen.findByText(t.foreignReferenceBlocked)).toBeVisible();
    expect(screen.queryByText(t.conflictDraftKept)).toBeNull();
  });
});

describe("Admin çalışma alanı", () => {
  it("oyun seçilince sürüm ve durum başlığını gösterir; arşivleme gerekçe ve onay ister", async () => {
    const record = { gameId: gid(1), status: "ACTIVE", version: 4, game: { game: card(1), description: "Açıklama", releaseDate: null, freshness: { status: "FRESH", fetchedAt: null }, metadata: { developers: [], publishers: [], languages: [], genres: [], platforms: [], earlyAccess: false } }, manualOverrides: [{ field: "TITLE", markedAt: "2026-01-01T00:00:00Z" }] };
    serve({ discovery: () => picks(1), admin: () => record, grants: () => [], adminAchievements: () => ({ items: [], totalElements: 0 }) });
    wrap(<GameAdminTools />);
    fireEvent.change(screen.getByRole("searchbox", { name: t.findGame }), { target: { value: "Fix" } });
    fireEvent.click(await screen.findByRole("button", { name: /Fixture 1/ }, { timeout: 2000 }));
    expect(await screen.findByRole("heading", { name: "Fixture 1" })).toBeVisible();
    expect(screen.getByText(t.recordVersion.replace("{version}", "4"))).toBeVisible();
    expect(screen.getByRole("form", { name: t.editGame })).toBeVisible();
    expect(screen.getByText(t.manualOverridesHelp)).toBeVisible();
    fireEvent.click(screen.getByRole("tab", { name: t.lifecycle }));
    const archive = screen.getByRole("form", { name: t.archiveGame });
    expect(within(archive).getByRole("button", { name: t.save })).toBeDisabled();
    expect(screen.queryByRole("form", { name: t.editGame })).toBeNull();
  });
  it("geliştirici yetkisini listeler ve geri almak için gerekçe + onay ister", async () => {
    const record = { gameId: gid(1), status: "ACTIVE", version: 1, game: { game: card(1), description: null, releaseDate: null, freshness: { status: "FRESH", fetchedAt: null }, metadata: { developers: [], publishers: [], languages: [], genres: [], platforms: [] } }, manualOverrides: [] };
    const developerUserId = "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66";
    serve({ admin: () => record, grants: () => [{ grantId: gid(7), gameId: gid(1), developerUserId, grantedAt: "2026-01-01T00:00:00Z" }] });
    wrap(<GameAdminTools initialGameId={gid(1)} />);
    await screen.findByRole("heading", { name: "Fixture 1" });
    fireEvent.click(screen.getByRole("tab", { name: t.developerGrants }));
    expect(await screen.findByText(developerUserId)).toBeVisible();
    const revoke = screen.getByRole("form", { name: t.revokeDeveloper });
    fireEvent.change(within(revoke).getByLabelText(t.fields.reason), { target: { value: "Yetki sona erdi" } });
    fireEvent.click(within(revoke).getByLabelText(t.confirmAction));
    await act(async () => { fireEvent.click(within(revoke).getByRole("button", { name: t.save })); });
    expect(mutate).toHaveBeenCalledWith({ operation: "revoke", gameId: gid(1), targetId: developerUserId, body: { reason: "Yetki sona erdi" } });
  });
  it("oyun oluşturma formu çoklu değerleri çip olarak alır ve sunucunun belirlediği alanları göndermez", async () => {
    serve({});
    wrap(<GameAdminTools />);
    const form = screen.getByRole("form", { name: t.createGame });
    fireEvent.change(within(form).getByLabelText(t.fields.title), { target: { value: "Yeni oyun" } });
    const developers = within(form).getByLabelText(t.fields.developers);
    fireEvent.change(developers, { target: { value: "Stüdyo A" } });
    fireEvent.keyDown(developers, { key: "Enter" });
    fireEvent.change(developers, { target: { value: "Stüdyo B, Stüdyo A" } });
    fireEvent.keyDown(developers, { key: "Enter" });
    expect(within(form).getAllByRole("button", { name: /değerini kaldır/ })).toHaveLength(2);
    fireEvent.submit(form);
    await waitFor(() => expect(mutate).toHaveBeenCalledTimes(1));
    const body = mutate.mock.calls[0][0].body as Record<string, unknown>;
    expect(body).toMatchObject({ title: "Yeni oyun", gameType: "MAIN_GAME", developers: ["Stüdyo A", "Stüdyo B"] });
    expect(["gameId", "origin", "status", "version"].filter((key) => key in body)).toEqual([]);
  });
});
