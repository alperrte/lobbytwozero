import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import messages from "@/shared/lib/i18n/messages/tr.json";
import { loadGameResource, mutateGame } from "../services/game-api";
import type { Feedback, GameResource } from "../types/game";
import { GameDeveloperTools } from "./game-developer-tools";
import { GameFeedback } from "./game-feedback";
import { GameGallery } from "./game-gallery";

vi.mock("@/shared/lib/i18n/navigation", () => ({ Link: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={String(href)}>{children}</a> }));
vi.mock("../services/game-api", async (original) => ({ ...await original<typeof import("../services/game-api")>(), loadGameResource: vi.fn(), mutateGame: vi.fn() }));
const load = vi.mocked(loadGameResource);
const mutate = vi.mocked(mutateGame);
const t = messages.Game;
const accountId = "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66";
const gameId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const item = (id: number, status: Feedback["status"], extra: Partial<Feedback> = {}): Feedback => ({ id: `0198a3c2-00${id}0-7d44-9a55-3f1c2b4d5e66`, gameId, type: "BUG_REPORT", status, title: `Geri bildirim ${id}`, body: `Ayrıntı ${id}`, version: id, createdAt: "2026-11-01T10:00:00Z", updatedAt: "2026-11-02T10:00:00Z", ...extra });
function serve(handlers: Partial<Record<GameResource["kind"], (resource: GameResource) => unknown>>) {
  load.mockImplementation(async (resource) => { const handler = handlers[resource.kind]; return handler ? handler(resource) : undefined; });
}
function wrap(node: React.ReactNode) { return render(<NextIntlClientProvider locale="tr" messages={messages}>{node}</NextIntlClientProvider>); }
beforeEach(() => { mutate.mockResolvedValue(undefined); });
afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe("Oyuncu geri bildirim formu", () => {
  it("erken erişim türünü yalnız erken erişimdeki oyunda sunar ve gönderimi sözleşmeye uygun yapar", async () => {
    serve({ myFeedback: () => ({ items: [], page: 0, size: 20, totalElements: 0 }) });
    wrap(<GameFeedback gameId={gameId} />);
    const form = await screen.findByRole("form", { name: t.sendFeedback });
    expect(within(form).queryByRole("option", { name: t.choices.EARLY_ACCESS })).toBeNull();
    fireEvent.change(within(form).getByLabelText(t.fields.type), { target: { value: "BUG_REPORT" } });
    fireEvent.change(within(form).getByLabelText(t.fields.title), { target: { value: "Çökme" } });
    fireEvent.change(within(form).getByLabelText(t.fields.body), { target: { value: "Başlangıçta çöküyor" } });
    fireEvent.submit(form);
    await waitFor(() => expect(mutate).toHaveBeenCalledTimes(1));
    expect(mutate.mock.calls[0][0]).toEqual({ operation: "feedback", body: { gameId, type: "BUG_REPORT", title: "Çökme", body: "Başlangıçta çöküyor" } });
    cleanup();
    wrap(<GameFeedback gameId={gameId} earlyAccess />);
    expect(within(await screen.findByRole("form", { name: t.sendFeedback })).getByRole("option", { name: t.choices.EARLY_ACCESS })).toBeVisible();
  });
});

describe("Geliştirici geri bildirim kutusu", () => {
  it("OPEN → IN_PROGRESS → RESOLVED geçişini sürümle yapar; çözülmüş kayıtta yeniden açma sunmaz", async () => {
    serve({ feedback: () => ({ items: [item(1, "OPEN"), item(2, "IN_PROGRESS"), item(3, "RESOLVED")], page: 0, size: 20, totalElements: 3 }) });
    wrap(<GameFeedback gameId={gameId} developer />);
    await screen.findByText("Geri bildirim 1");
    expect(screen.getAllByRole("button", { name: t.startFeedback })).toHaveLength(1);
    expect(screen.getAllByRole("button", { name: t.resolveFeedback })).toHaveLength(1);
    expect(screen.queryByRole("button", { name: /yeniden aç/i })).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: t.startFeedback }));
    await waitFor(() => expect(mutate).toHaveBeenCalledTimes(1));
    expect(mutate.mock.calls[0][0]).toEqual({ operation: "feedbackStatus", gameId, targetId: item(1, "OPEN").id, body: { status: "IN_PROGRESS", version: 1 } });
    fireEvent.click(await screen.findByRole("button", { name: t.resolveFeedback }));
    await waitFor(() => expect(mutate).toHaveBeenCalledTimes(2));
    expect(mutate.mock.calls[1][0].body).toEqual({ status: "RESOLVED", version: 2 });
  });
  it("tür ve durum süzgeçlerini istek parametresine taşır", async () => {
    serve({ feedback: () => ({ items: [], page: 0, size: 20, totalElements: 0 }) });
    wrap(<GameFeedback gameId={gameId} developer />);
    await screen.findByText(t.emptyFeedbackDeveloper);
    fireEvent.change(screen.getByLabelText(t.fields.status), { target: { value: "OPEN" } });
    fireEvent.change(screen.getByLabelText(t.fields.type), { target: { value: "BUG_REPORT" } });
    await waitFor(() => expect(load.mock.calls.some(([resource]) => resource.kind === "feedback" && resource.status === "OPEN" && resource.type === "BUG_REPORT")).toBe(true));
  });
});

describe("Geliştirici içerik yönetimi", () => {
  const content = { gameId, description: { text: "Gizlenmiş açıklama", version: 2, status: "HIDDEN", moderationReason: "Uygunsuz", updatedAt: "2026-11-01T10:00:00Z" }, media: [{ id: "0198a3c2-0111-7d44-9a55-3f1c2b4d5e66", type: "IMAGE", url: "https://images.example/a.png", altText: "Ekran", status: "VISIBLE", createdAt: "2026-11-01T10:00:00Z" }, { id: "0198a3c2-0222-7d44-9a55-3f1c2b4d5e66", type: "DEMO", url: "https://demo.example/play", altText: null, status: "HIDDEN", moderationReason: "Zararlı bağlantı", createdAt: "2026-11-01T10:00:00Z" }], updateNotes: [] };
  function open() {
    serve({ session: () => ({ accountId, roles: ["GAME_DEVELOPER"] }), managed: () => [{ gameId, title: "Fixture oyun" }], developer: () => content, feedback: () => ({ items: [], page: 0, size: 20, totalElements: 0 }) });
    wrap(<GameDeveloperTools />);
  }
  it("moderasyonla gizlenen açıklamayı düzenlemeye kapatır ve gerekçeyi gösterir", async () => {
    open();
    expect(await screen.findByText(/Uygunsuz/)).toBeVisible();
    expect(screen.getByText(t.hiddenEditBlocked)).toBeVisible();
    expect(screen.getByRole("button", { name: t.save })).toBeDisabled();
  });
  it("medya sekmesinde önizleme, bağlantı ve moderasyon durumunu gösterir", async () => {
    open();
    fireEvent.click(await screen.findByRole("tab", { name: t.tabs.media }));
    expect(screen.getByText("Ekran")).toBeVisible();
    expect(screen.getByRole("link", { name: t.openDemo })).toHaveAttribute("href", "https://demo.example/play");
    expect(screen.getByText(/Zararlı bağlantı/)).toBeVisible();
    expect(document.querySelector("input[type=file]")).toBeNull();
    expect(screen.getByText(t.mediaUrlOnly)).toBeVisible();
  });
  it("yetkili oyun yoksa boş durum gösterir", async () => {
    serve({ session: () => ({ accountId, roles: ["GAME_DEVELOPER"] }), managed: () => [] });
    wrap(<GameDeveloperTools />);
    expect(await screen.findByText(t.noManagedGames)).toBeVisible();
  });
});

describe("Görsel galerisi", () => {
  it("yalnız güvenli HTTPS görselleri ve bağlantıları gösterir", () => {
    wrap(<GameGallery images={[{ url: "javascript:alert(1)", alt: "kötü" }, { url: "http://insecure.example/a.png", alt: "http" }, { url: "https://images.example/a.png", alt: "iyi" }]} links={[{ url: "data:text/html,x", label: "Kötü video" }, { url: "https://video.example/watch", label: "Fragman" }]} />);
    expect(screen.getAllByRole("img")).toHaveLength(1);
    expect(screen.getByRole("img", { name: "iyi" })).toBeVisible();
    expect(screen.queryByRole("link", { name: /Kötü video/ })).toBeNull();
    const link = screen.getByRole("link", { name: /Fragman/ });
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });
  it("ok tuşları ve düğmelerle gezinir", () => {
    wrap(<GameGallery images={[{ url: "https://images.example/1.png", alt: "bir" }, { url: "https://images.example/2.png", alt: "iki" }, { url: "https://images.example/3.png", alt: "üç" }]} />);
    const group = screen.getByRole("group", { name: t.gallery });
    expect(screen.getAllByRole("img", { name: "bir" })).toHaveLength(1);
    fireEvent.keyDown(group, { key: "ArrowRight" });
    expect(screen.getByRole("img", { name: "iki" })).toBeVisible();
    fireEvent.keyDown(group, { key: "ArrowLeft" });
    fireEvent.keyDown(group, { key: "ArrowLeft" });
    expect(screen.getByRole("img", { name: "üç" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: t.galleryShow.replace("{index}", "1") }));
    expect(screen.getByRole("img", { name: "bir" })).toBeVisible();
  });
  it("hiç güvenli içerik yoksa hiçbir şey çizmez", () => {
    const { container } = wrap(<GameGallery images={[{ url: "http://x.example/a.png", alt: "x" }]} />);
    expect(container).toBeEmptyDOMElement();
  });
});
