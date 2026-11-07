import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, describe, expect, it, vi } from "vitest";
import messages from "@/shared/lib/i18n/messages/tr.json";
import { ApiHttpError } from "@/shared/lib/api/client";
import { loadGameResource } from "../services/game-api";
import { GameModules } from "./game-modules";

vi.mock("../services/game-api", () => ({ loadGameResource: vi.fn() }));
const load = vi.mocked(loadGameResource);
const gameId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const contentId = "0198a3c2-9999-7d44-9a55-3f1c2b4d5e66";
function mount() {
  render(<NextIntlClientProvider locale="tr" messages={messages}><GameModules gameId={gameId} /></NextIntlClientProvider>);
}
afterEach(() => vi.resetAllMocks());

describe("Oyun modülleri", () => {
  it("Community listesini, sayfalamasını ve düz metin detayını gösterir", async () => {
    load.mockResolvedValueOnce({ kind: "achievements", items: [], totalElements: 0, page: 0, size: 20 });
    mount();
    await screen.findByText(messages.Game.emptyModule);
    load.mockResolvedValueOnce({ kind: "community", items: [{ id: contentId, gameId, type: "GUIDES", title: "<b>Rehber</b>", excerpt: "İlk adım", authorId: gameId, publishedAt: "2026-11-05T10:00:00Z", updatedAt: "2026-11-05T10:00:00Z" }], totalElements: 21, page: 0, size: 20 });
    fireEvent.click(screen.getByRole("button", { name: "Rehberler" }));
    await screen.findByRole("button", { name: "<b>Rehber</b>" });
    expect(document.querySelector("b")).toBeNull();
    load.mockResolvedValueOnce({ id: contentId, gameId, type: "GUIDES", title: "Rehber", excerpt: "İlk adım", authorId: gameId, publishedAt: "2026-11-05T10:00:00Z", updatedAt: "2026-11-05T10:00:00Z", body: "<script>alert(1)</script>", likeCount: 2, commentCount: 1, likedByMe: false });
    fireEvent.click(screen.getByRole("button", { name: "<b>Rehber</b>" }));
    await screen.findByText("<script>alert(1)</script>");
    expect(document.querySelector("article script")).toBeNull();
    load.mockResolvedValueOnce({ kind: "community", items: [], totalElements: 21, page: 0, size: 20 });
    fireEvent.click(screen.getByRole("button", { name: "Listeye dön" }));
    await screen.findByText(messages.Game.emptyModule);
    load.mockResolvedValueOnce({ kind: "community", items: [], totalElements: 21, page: 1, size: 20 });
    fireEvent.click(screen.getByRole("button", { name: "Sonraki" }));
    await waitFor(() => expect(load).toHaveBeenLastCalledWith({ kind: "module", gameId, type: "GUIDES", page: 1 }, expect.any(AbortSignal)));
  });
  it.each([401, 404, 429, 500, 503])("%s hatasını boş içerikten ayırır ve yeniden denemeyi sağlar", async (status) => {
    load.mockRejectedValueOnce(new ApiHttpError(status));
    mount();
    expect(await screen.findByRole("alert")).toHaveTextContent(messages.Game.errors[String(status) as keyof typeof messages.Game.errors]);
    expect(screen.queryByText(messages.Game.emptyModule)).toBeNull();
    load.mockResolvedValueOnce({ kind: "achievements", items: [], totalElements: 0, page: 0, size: 20 });
    fireEvent.click(screen.getByRole("button", { name: "Tekrar dene" }));
    await screen.findByText(messages.Game.emptyModule);
  });
  it("yeniden odaklanınca eski kişisel içeriği kaldırıp güncel görünürlüğü okur", async () => {
    load.mockResolvedValueOnce({ kind: "achievements", items: [{ id: contentId, title: "Eski içerik", description: null }], totalElements: 1, page: 0, size: 20 });
    mount();
    await screen.findByText("Eski içerik");
    load.mockReturnValueOnce(new Promise(() => {}));
    fireEvent.focus(window);
    await waitFor(() => expect(screen.queryByText("Eski içerik")).toBeNull());
    expect(screen.getByRole("status")).toHaveTextContent(messages.Game.loading);
  });
});
