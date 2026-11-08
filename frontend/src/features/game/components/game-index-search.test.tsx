import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, describe, expect, it, vi } from "vitest";
import messages from "@/shared/lib/i18n/messages/tr.json";
import { ApiHttpError } from "@/shared/lib/api/client";
import { loadGameResource } from "../services/game-api";
import type { CatalogSearchPage } from "../types/game";
import { GameIndexSearch } from "./game-index-search";

vi.mock("@/shared/lib/i18n/navigation", () => ({
  Link: ({ href, children, ...rest }: { href: { pathname: string; params?: { gameId: string }; query?: Record<string, string> }; children: React.ReactNode } & Record<string, unknown>) => <a {...rest} href={href.params ? `/games/${href.params.gameId}` : `${href.pathname}?${new URLSearchParams(href.query).toString()}`}>{children}</a>
}));
vi.mock("../services/game-api", async (original) => ({ ...await original<typeof import("../services/game-api")>(), loadGameResource: vi.fn() }));
const load = vi.mocked(loadGameResource);
const t = messages.Game;
const id = "0198a3c2-0001-7d44-9a55-3f1c2b4d5e66";
const page = (items: CatalogSearchPage["items"], totalElements = items.length): CatalogSearchPage => ({ items, page: 0, size: 20, totalElements, lastSuccessfulAt: null, providerTotal: null });
function mount() { return render(<NextIntlClientProvider locale="tr" messages={messages}><GameIndexSearch /></NextIntlClientProvider>); }
const search = (text: string) => { fireEvent.change(screen.getByRole("searchbox", { name: t.search }), { target: { value: text } }); fireEvent.click(screen.getByRole("button", { name: t.indexSearchButton })); };
afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe("Steam indeks araması (yönetim)", () => {
  it("boş aramada istek atmaz; arama gönderilince q ve sınıflandırılmamış seçeneğiyle istek atar", async () => {
    load.mockResolvedValue(page([{ gameId: id, title: "Indexed Game", origin: "EXTERNAL", metadataCompleteness: "INDEX_ONLY", gameType: "UNKNOWN", coverUrl: null }]));
    mount();
    expect(screen.getByRole("button", { name: t.indexSearchButton })).toBeDisabled();
    expect(load).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("checkbox", { name: t.includeUnclassified }));
    search("  Indexed ");
    const row = (await screen.findByText("Indexed Game")).closest("li")!;
    expect(load).toHaveBeenCalledWith({ kind: "catalogSearch", q: "Indexed", page: 0, includeUnclassified: true }, expect.any(AbortSignal));
    expect(within(row).getByText(t.choices.UNKNOWN)).toBeVisible();
    expect(within(row).getByRole("link", { name: t.openInAdmin })).toHaveAttribute("href", `/games/manage?section=admin&gameId=${id}`);
  });
  it("sonuç yoksa boş durum gösterir; hata durumunda anlaşılır mesaj ve yeniden deneme sunar", async () => {
    load.mockResolvedValueOnce(page([]));
    mount(); search("none");
    expect(await screen.findByText(t.indexNoResults)).toBeVisible();
    load.mockRejectedValueOnce(new ApiHttpError(503));
    search("again");
    expect(await screen.findByText(t.errors["503"])).toBeVisible();
    expect(screen.getByRole("button", { name: t.retry })).toBeVisible();
  });
  it("sağlayıcı çağrısı yapmadığını açıklar", () => {
    mount();
    expect(screen.getByText(t.indexSearchHelp)).toBeVisible();
  });
});
