import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { loadReviewResource } from "../services/review-api";
import type { ReviewGameSearchPage, ReviewResource } from "../types/review";
import { ReviewHub } from "./review-hub";

const mocks = vi.hoisted(() => ({ query: "", replace: vi.fn(), push: vi.fn(), me: { accountId: "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66" } as { accountId: string; adultVerified?: boolean } }));
vi.mock("@/features/auth", () => ({
  useRequireSession: () => ({ status: "authenticated", me: mocks.me }),
  useSession: () => ({ status: "authenticated", me: mocks.me })
}));
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(mocks.query), usePathname: () => "/tr/incelemeler", useRouter: () => mocks }));
vi.mock("@/shared/lib/i18n/navigation", () => ({
  useRouter: () => mocks,
  Link: ({ href, children, ...props }: { href: string | { pathname: string; params?: Record<string, string> }; children: ReactNode }) => {
    const target = typeof href === "string" ? href : Object.entries(href.params ?? {}).reduce((path, [key, value]) => path.replace(`[${key}]`, value), href.pathname);
    return <a href={target} {...props}>{children}</a>;
  }
}));
vi.mock("next/image", () => ({ default: () => <span data-testid="cover-image" /> }));
vi.mock("../services/review-api", () => ({ loadReviewResource: vi.fn(), reviewApi: {}, reviewMutations: {}, findOwnReview: vi.fn(), OWN_REVIEW_SCAN_PAGES: 20 }));

const first = "0198a3c2-1111-7d44-9a55-3f1c2b4d5e66";
const second = "0198a3c2-2222-7d44-9a55-3f1c2b4d5e66";
const load = vi.mocked(loadReviewResource);
let games: ReviewGameSearchPage | Error;
let summaries: unknown;

function wrap(children: ReactNode) {
  return <NextIntlClientProvider locale="tr" messages={tr}>{children}</NextIntlClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
  mocks.query = "";
  mocks.me = { accountId: "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66" };
  games = {
    items: [
      { game: { gameId: first, title: "Birinci Oyun", coverUrl: null }, releaseDate: null },
      { game: { gameId: second, title: "İkinci Oyun", coverUrl: null }, releaseDate: null }
    ],
    page: 0,
    size: 12,
    totalElements: 2
  };
  summaries = { items: [{ gameId: first, averageRating: 8.5, ratingCount: 40 }, { gameId: second, averageRating: null, ratingCount: 0 }] };
  load.mockImplementation(async (resource: ReviewResource) => {
    if (resource.kind === "gameSearch") {
      if (games instanceof Error) throw games;
      return games;
    }
    if (resource.kind === "ratingSummaries") {
      if (summaries instanceof Error) throw summaries;
      return summaries;
    }
    throw new Error("beklenmeyen kaynak");
  });
});

describe("İnceleme merkezi", () => {
  it("üç kavramı ve desteklenen işlemleri açıklar; sahte genel akış göstermez", async () => {
    render(wrap(<ReviewHub />));
    expect(await screen.findByRole("heading", { level: 1, name: "İncelemeler" })).toBeVisible();
    for (const concept of ["Puan", "Tavsiye", "İnceleme"]) {
      expect(screen.getByRole("heading", { level: 2, name: concept })).toBeVisible();
    }
    expect(screen.getByText(/seçmediysen "olumsuz" sayılmaz/)).toBeVisible();
    expect(screen.getByText(/tek bir akış/)).toBeVisible();
    expect(screen.queryByText(/incelemelerim. geçmişi/)).toBeNull();
    expect(screen.queryByRole("article", { name: /son incelemeler/i })).toBeNull();
  });

  it("İncelemelerim sayfasına bağlantı sunar", async () => {
    render(wrap(<ReviewHub />));
    expect(await screen.findByRole("link", { name: "İncelemelerim" })).toHaveAttribute("href", "/reviews/mine");
  });

  it("gerçek oyun kartlarını gösterir ve puanları TEK toplu istekle okur", async () => {
    render(wrap(<ReviewHub />));
    expect(await screen.findByText("Birinci Oyun")).toBeVisible();
    expect(await screen.findByText("LTZ puanı 8,5 / 10 · 40 puan")).toBeVisible();
    const summaryCalls = load.mock.calls.filter(([resource]) => resource.kind === "ratingSummaries");
    expect(summaryCalls).toHaveLength(1);
    expect(summaryCalls[0][0]).toEqual({ kind: "ratingSummaries", gameIds: [first, second] });
    expect(screen.getByRole("link", { name: "İncelemeleri aç: Birinci Oyun" })).toHaveAttribute("href", `/reviews/games/${first}`);
  });

  it("puansız oyunda 0/10 göstermez", async () => {
    render(wrap(<ReviewHub />));
    const card = (await screen.findByText("İkinci Oyun")).closest("article") as HTMLElement;
    expect(await within(card).findByText("Henüz puan yok")).toBeVisible();
    expect(within(card).queryByText(/0 \/ 10/)).toBeNull();
  });

  it("puan özeti alınamazsa oyunları yine gösterir ve puanı bilinmiyor diye işaretler", async () => {
    summaries = new ApiHttpError(503);
    render(wrap(<ReviewHub />));
    expect(await screen.findByText("Birinci Oyun")).toBeVisible();
    expect(await screen.findByText("Puan bilgileri şu anda alınamıyor.")).toBeVisible();
    expect(screen.getAllByText("Puan bilgisi alınamadı")).toHaveLength(2);
    expect(screen.getAllByRole("link", { name: /İncelemeleri aç/ })).toHaveLength(2);
  });

  it("aramayı URL sorgusuna yazar ve sayfayı sıfırlar", async () => {
    mocks.query = "page=3";
    render(wrap(<ReviewHub />));
    await screen.findByText("Birinci Oyun");
    fireEvent.change(screen.getByRole("searchbox", { name: "Katalogda oyun ara" }), { target: { value: "  half   life " } });
    fireEvent.click(screen.getByRole("button", { name: "Ara" }));
    expect(mocks.push).toHaveBeenCalledWith("/tr/incelemeler?q=half+life", { scroll: false });
  });

  it("URL'deki aramayı ve sayfayı istekte kullanır", async () => {
    mocks.query = "q=half&page=2";
    render(wrap(<ReviewHub />));
    await screen.findByText("Birinci Oyun");
    expect(load).toHaveBeenCalledWith({ kind: "gameSearch", q: "half", page: 2, size: 12 }, expect.any(AbortSignal));
    expect(screen.getByRole("searchbox", { name: "Katalogda oyun ara" })).toHaveValue("half");
  });

  it("sonuç yoksa nedeni ve temizleme eylemini gösterir", async () => {
    mocks.query = "q=yokboyleoyun";
    games = { items: [], page: 0, size: 12, totalElements: 0 };
    render(wrap(<ReviewHub />));
    expect(await screen.findByText('"yokboyleoyun" için oyun bulunamadı')).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Aramayı temizle" }));
    expect(mocks.push).toHaveBeenCalledWith("/tr/incelemeler", { scroll: false });
    expect(load.mock.calls.some(([resource]) => resource.kind === "ratingSummaries")).toBe(false);
  });

  it("sayfalamada sonraki sayfayı URL'ye yazar", async () => {
    games = { ...(games as ReviewGameSearchPage), totalElements: 30 };
    render(wrap(<ReviewHub />));
    await screen.findByText("Birinci Oyun");
    fireEvent.click(screen.getByRole("button", { name: "Sonraki" }));
    expect(mocks.push).toHaveBeenCalledWith("/tr/incelemeler?page=1", { scroll: false });
  });

  it("katalog hatasında hata ve tekrar dene gösterir", async () => {
    games = new ApiHttpError(500);
    render(wrap(<ReviewHub />));
    expect(await screen.findByText(/Sunucu işlemi tamamlayamadı/)).toBeVisible();
    games = { items: [], page: 0, size: 12, totalElements: 0 };
    fireEvent.click(screen.getByRole("button", { name: "Tekrar dene" }));
    await waitFor(() => expect(screen.queryByText(/Sunucu işlemi tamamlayamadı/)).toBeNull());
  });
});

describe("İnceleme merkezi: +18 kapısı", () => {
  const checkbox = () => screen.queryByRole("checkbox", { name: tr.Review.hub.adultToggle });
  const note = () => screen.queryByText(tr.Review.hub.adultNotApplied, { exact: false });
  const searchCalls = () => load.mock.calls.map(([resource]) => resource).filter((resource) => resource.kind === "gameSearch");
  const settingsLink = () => screen.getByRole("link", { name: tr.Review.hub.adultVerifyLink });

  it("yaşı doğrulanmamış kullanıcıda kutu yoktur; açıklama ve hesap ayarlarına bağlantı gösterilir, istek +18 içermez", async () => {
    for (const me of [{ accountId: "a" }, { accountId: "a", adultVerified: false }]) {
      mocks.me = me;
      const view = render(wrap(<ReviewHub />));
      await screen.findByText("Birinci Oyun");
      expect(checkbox()).toBeNull();
      expect(screen.getByText(tr.Review.hub.adultVerifyHint, { exact: false })).toBeVisible();
      expect(settingsLink()).toHaveAttribute("href", "/profile/settings");
      expect(searchCalls().every((resource) => !("includeAdult" in resource))).toBe(true);
      expect(note()).toBeNull();
      view.unmount();
      load.mockClear();
    }
  });

  it("yaşı doğrulanmış kullanıcıda kutu görünür, işaretlenince URL'ye yazılır ve sayfa sıfırlanır; arama metni korunur", async () => {
    mocks.me = { accountId: "a", adultVerified: true };
    mocks.query = "q=half&page=2";
    render(wrap(<ReviewHub />));
    await screen.findByText("Birinci Oyun");
    expect(checkbox()).not.toBeChecked();
    expect(screen.queryByRole("link", { name: tr.Review.hub.adultVerifyLink })).toBeNull();
    fireEvent.click(checkbox()!);
    expect(mocks.push).toHaveBeenCalledWith("/tr/incelemeler?q=half&includeAdult=true", { scroll: false });
  });

  it("URL'de includeAdult=true ve doğrulanmışsa istek +18 içerir; kutu işaretlidir; sunucu uyguladıysa ipucu yoktur", async () => {
    mocks.me = { accountId: "a", adultVerified: true };
    mocks.query = "includeAdult=true";
    games = { ...(games as ReviewGameSearchPage), includeAdultApplied: true };
    render(wrap(<ReviewHub />));
    await screen.findByText("Birinci Oyun");
    expect(load).toHaveBeenCalledWith({ kind: "gameSearch", q: "", page: 0, size: 12, includeAdult: true }, expect.any(AbortSignal));
    expect(checkbox()).toBeChecked();
    expect(note()).toBeNull();
    fireEvent.click(checkbox()!);
    expect(mocks.push).toHaveBeenCalledWith("/tr/incelemeler", { scroll: false });
  });

  it("sunucu includeAdultApplied=false derse (doğrulama geri alındı, iddia gecikmeli) yerel durum doğrulanmış olsa da ipucu gösterilir", async () => {
    mocks.me = { accountId: "a", adultVerified: true };
    mocks.query = "includeAdult=true";
    games = { ...(games as ReviewGameSearchPage), includeAdultApplied: false };
    render(wrap(<ReviewHub />));
    await screen.findByText("Birinci Oyun");
    expect(note()).toBeVisible();
    expect(settingsLink()).toHaveAttribute("href", "/profile/settings");
  });

  it("doğrulanmamış kullanıcıda URL'deki includeAdult=true sunucuya gönderilmez ama ipucu gösterilir", async () => {
    mocks.query = "includeAdult=true";
    render(wrap(<ReviewHub />));
    await screen.findByText("Birinci Oyun");
    expect(searchCalls().every((resource) => !("includeAdult" in resource))).toBe(true);
    expect(note()).toBeVisible();
  });

  it("geçersiz includeAdult değerleri (1, tekrarlı) yok sayılır: istek +18 içermez ve ipucu çıkmaz", async () => {
    mocks.me = { accountId: "a", adultVerified: true };
    for (const query of ["includeAdult=1", "includeAdult=true&includeAdult=true"]) {
      mocks.query = query;
      const view = render(wrap(<ReviewHub />));
      await screen.findByText("Birinci Oyun");
      expect(searchCalls().every((resource) => !("includeAdult" in resource))).toBe(true);
      expect(note()).toBeNull();
      view.unmount();
      load.mockClear();
    }
  });

  it("includeAdult istenmediyse (eski sunucuda includeAdultApplied alanı yoksa da) ipucu çıkmaz", async () => {
    mocks.me = { accountId: "a", adultVerified: true };
    render(wrap(<ReviewHub />));
    await screen.findByText("Birinci Oyun");
    expect(note()).toBeNull();
  });

  it("yetişkin oyun kartında +18 rozeti gösterilir; false, null ve alan yokken gösterilmez", async () => {
    games = {
      items: [
        { game: { gameId: first, title: "Birinci Oyun", coverUrl: null }, releaseDate: null, adult: true },
        { game: { gameId: second, title: "İkinci Oyun", coverUrl: null }, releaseDate: null, adult: false },
        { game: { gameId: "0198a3c2-3333-7d44-9a55-3f1c2b4d5e66", title: "Üçüncü Oyun", coverUrl: null }, releaseDate: null, adult: null },
        { game: { gameId: "0198a3c2-4444-7d44-9a55-3f1c2b4d5e66", title: "Dördüncü Oyun", coverUrl: null }, releaseDate: null }
      ],
      page: 0,
      size: 12,
      totalElements: 4
    };
    render(wrap(<ReviewHub />));
    const adultCard = (await screen.findByText("Birinci Oyun")).closest("article") as HTMLElement;
    expect(within(adultCard).getByTestId("adult-badge")).toHaveTextContent(tr.Review.hub.adultBadge);
    expect(screen.getAllByTestId("adult-badge")).toHaveLength(1);
  });
});
