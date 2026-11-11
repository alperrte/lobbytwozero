import { fireEvent, render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { loadReviewResource } from "../services/review-api";
import type { ReviewGameCard, ReviewPage, ReviewResource } from "../types/review";
import { MyReviewsPage } from "./my-reviews-page";

const mocks = vi.hoisted(() => ({ query: "", push: vi.fn(), replace: vi.fn() }));
vi.mock("@/features/auth", () => ({ useRequireSession: () => ({ status: "authenticated", me: { accountId: "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66" } }) }));
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(mocks.query), usePathname: () => "/tr/incelemeler/incelemelerim", useRouter: () => mocks }));
vi.mock("@/shared/lib/i18n/navigation", () => ({
  useRouter: () => mocks,
  Link: ({ href, children, ...props }: { href: string | { pathname: string; params?: Record<string, string> }; children: ReactNode }) => {
    const target = typeof href === "string" ? href : Object.entries(href.params ?? {}).reduce((path, [key, value]) => path.replace(`[${key}]`, value), href.pathname);
    return <a href={target} {...props}>{children}</a>;
  }
}));
vi.mock("next/image", () => ({ default: () => <span data-testid="cover-image" /> }));
vi.mock("../services/review-api", () => ({ loadReviewResource: vi.fn(), reviewApi: {}, reviewMutations: {} }));

const gameA = "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66";
const gameB = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const load = vi.mocked(loadReviewResource);
let page: ReviewPage | Error;
let cards: ReviewGameCard[] | Error;

const review = (id: string, gameId: string, body: string, updated = "2026-11-01T12:00:00Z") => ({ id, gameId, body, ownedByActor: true, version: 1, createdAt: "2026-11-01T12:00:00Z", updatedAt: updated });

function wrap(children: ReactNode) {
  return <NextIntlClientProvider locale="tr" messages={tr}>{children}</NextIntlClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
  mocks.query = "";
  page = {
    items: [review("0198a3c2-1111-7d44-9a55-3f1c2b4d5e66", gameA, "Birinci <b>inceleme</b>", "2026-11-02T12:00:00Z"), review("0198a3c2-2222-7d44-9a55-3f1c2b4d5e66", gameB, "İkinci inceleme")],
    page: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1
  };
  cards = [{ gameId: gameA, title: "Oyun A", coverUrl: null }, { gameId: gameB, title: "Oyun B", coverUrl: null }];
  load.mockImplementation(async (resource: ReviewResource) => {
    if (resource.kind === "myReviews") {
      if (page instanceof Error) throw page;
      return page;
    }
    if (resource.kind === "gameCards") {
      if (cards instanceof Error) throw cards;
      return cards;
    }
    throw new Error("beklenmeyen kaynak");
  });
});

describe("İncelemelerim", () => {
  it("tüm oyunlardaki incelemeleri gerçek oyun adlarıyla ve toplamla listeler", async () => {
    render(wrap(<MyReviewsPage />));
    expect(await screen.findByRole("heading", { level: 1, name: "İncelemelerim" })).toBeVisible();
    expect(await screen.findByRole("heading", { name: "Oyun A" })).toBeVisible();
    expect(screen.getByRole("heading", { name: "Oyun B" })).toBeVisible();
    expect(screen.getByText("2 inceleme")).toBeVisible();
    const first = screen.getAllByRole("article")[0];
    expect(within(first).getByText(/Düzenlendi/)).toBeVisible();
    expect(within(first).getByRole("link", { name: "Aç ve yönet" })).toHaveAttribute("href", "/reviews/0198a3c2-1111-7d44-9a55-3f1c2b4d5e66");
    expect(within(first).getByRole("link", { name: "Oyun incelemeleri" })).toHaveAttribute("href", `/reviews/games/${gameA}`);
  });

  it("oyun adlarını sayfa başına TEK toplu istekle alır", async () => {
    render(wrap(<MyReviewsPage />));
    await screen.findByRole("heading", { name: "Oyun A" });
    const cardCalls = load.mock.calls.filter(([resource]) => resource.kind === "gameCards");
    expect(cardCalls).toHaveLength(1);
    expect(cardCalls[0][0]).toEqual({ kind: "gameCards", gameIds: [gameA, gameB] });
  });

  it("inceleme metnini düz metin olarak çizer", async () => {
    render(wrap(<MyReviewsPage />));
    const text = await screen.findByText("Birinci <b>inceleme</b>");
    expect(text.querySelector("b")).toBeNull();
  });

  it("Game ulaşılamazsa incelemeleri yine gösterir; oyun adı uydurmaz", async () => {
    cards = new ApiHttpError(503);
    render(wrap(<MyReviewsPage />));
    expect(await screen.findByText("Birinci <b>inceleme</b>")).toBeVisible();
    expect(await screen.findByText("Oyun adları şu anda alınamıyor.")).toBeVisible();
    expect(screen.getAllByRole("heading", { name: "Oyun" })).toHaveLength(2);
  });

  it("inceleme yoksa nedeni ve sonraki adımı açıklar; toplu kart isteği atmaz", async () => {
    page = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
    render(wrap(<MyReviewsPage />));
    expect(await screen.findByText("Henüz inceleme yazmadın")).toBeVisible();
    expect(screen.getByRole("link", { name: "Oyun seç" })).toHaveAttribute("href", "/reviews");
    expect(load.mock.calls.some(([resource]) => resource.kind === "gameCards")).toBe(false);
  });

  it("URL sayfasını okur ve sayfalamayı URL'ye yazar", async () => {
    mocks.query = "page=1";
    page = { ...(page as ReviewPage), page: 1, totalElements: 45, totalPages: 3 };
    render(wrap(<MyReviewsPage />));
    await screen.findByText("45 inceleme");
    expect(load).toHaveBeenCalledWith({ kind: "myReviews", page: 1, size: 20 }, expect.any(AbortSignal));
    fireEvent.click(screen.getByRole("button", { name: "Sonraki" }));
    expect(mocks.push).toHaveBeenCalledWith("/tr/incelemeler/incelemelerim?page=2", { scroll: false });
  });

  it("hata durumunda tekrar dene sunar", async () => {
    page = new ApiHttpError(500);
    render(wrap(<MyReviewsPage />));
    expect(await screen.findByText(/Sunucu işlemi tamamlayamadı/)).toBeVisible();
    expect(screen.getByRole("button", { name: "Tekrar dene" })).toBeVisible();
  });
});
