import { render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import en from "@/shared/lib/i18n/messages/en.json";
import { loadReviewResource } from "../services/review-api";
import type { GameReviewStatistics, ReviewResource } from "../types/review";
import { ReviewStatisticsPage } from "./review-statistics-page";

vi.mock("@/features/auth", () => ({ useRequireSession: () => ({ status: "authenticated", me: { accountId: "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66" } }) }));
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(), usePathname: () => "/tr/incelemeler/oyun/x/istatistikler", useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));
vi.mock("@/shared/lib/i18n/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  Link: ({ href, children, ...props }: { href: string | { pathname: string; params?: Record<string, string> }; children: ReactNode }) => {
    const target = typeof href === "string" ? href : Object.entries(href.params ?? {}).reduce((path, [key, value]) => path.replace(`[${key}]`, value), href.pathname);
    return <a href={target} {...props}>{children}</a>;
  }
}));
vi.mock("next/image", () => ({ default: () => <span data-testid="cover-image" /> }));
vi.mock("../services/review-api", () => ({ loadReviewResource: vi.fn(), reviewApi: {}, reviewMutations: {}, findOwnReview: vi.fn(), OWN_REVIEW_SCAN_PAGES: 20 }));

const gameId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const load = vi.mocked(loadReviewResource);
const buckets = (counts: number[]) => counts.map((userCount, index) => ({ rating: index + 1, userCount }));
let stats: GameReviewStatistics | Error;

function wrap(children: ReactNode, locale: "tr" | "en" = "tr") {
  return <NextIntlClientProvider locale={locale} messages={locale === "tr" ? tr : en}>{children}</NextIntlClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
  stats = {
    gameId,
    ratings: { ratingCount: 6, averageRating: 5.5, distribution: buckets([2, 0, 0, 0, 0, 0, 1, 1, 1, 1]) },
    reviews: { activeCount: 2 },
    recommendations: { recommendedCount: 1, notRecommendedCount: 3, totalCount: 4, recommendedPercentage: 25, notRecommendedPercentage: 75 }
  };
  load.mockImplementation(async (resource: ReviewResource) => {
    if (resource.kind === "statistics") {
      if (stats instanceof Error) throw stats;
      return stats;
    }
    if (resource.kind === "gameCard") return { gameId, title: "Test Oyunu", coverUrl: null };
    throw new Error("beklenmeyen kaynak");
  });
});

describe("İstatistik ekranı", () => {
  it("10 puan kovasının tamamını (boş olanlar 0 ile) 10'dan 1'e sıralı gösterir", async () => {
    render(wrap(<ReviewStatisticsPage gameId={gameId} />));
    const list = await screen.findByRole("list", { name: "Puan dağılımı" });
    const rows = within(list).getAllByRole("listitem");
    expect(rows).toHaveLength(10);
    expect(rows.map((row) => row.querySelector(".sr-only")?.textContent)).toEqual([
      "10 puan: 1 kullanıcı", "9 puan: 1 kullanıcı", "8 puan: 1 kullanıcı", "7 puan: 1 kullanıcı", "6 puan: 0 kullanıcı",
      "5 puan: 0 kullanıcı", "4 puan: 0 kullanıcı", "3 puan: 0 kullanıcı", "2 puan: 0 kullanıcı", "1 puan: 2 kullanıcı"
    ]);
  });

  it("ortalama, puan veren, aktif inceleme ve tavsiye sayılarını AYRI ölçüler olarak gösterir", async () => {
    render(wrap(<ReviewStatisticsPage gameId={gameId} />));
    const overview = (await screen.findByText("Ortalama LTZ puanı")).closest("dl") as HTMLElement;
    expect(within(overview).getByText("5,5 / 10")).toBeVisible();
    expect(within(overview).getByText("Puan veren kullanıcı").nextElementSibling).toHaveTextContent("6");
    expect(within(overview).getByText("Aktif inceleme").nextElementSibling).toHaveTextContent("2");
    expect(within(overview).getByText("Olumlu tavsiye").nextElementSibling).toHaveTextContent("25%");
    expect(within(overview).getByText("1 olumlu · 3 olumsuz")).toBeVisible();
  });

  it("tavsiye dağılımında sunucunun yüzdelerini ve sayıları gösterir", async () => {
    render(wrap(<ReviewStatisticsPage gameId={gameId} />));
    const panel = (await screen.findByRole("heading", { name: "Tavsiye dağılımı" })).closest("section") as HTMLElement;
    expect(within(panel).getByText("25%")).toBeVisible();
    expect(within(panel).getByText("75%")).toBeVisible();
    expect(within(panel).getByText("3 kullanıcı")).toBeVisible();
    expect(within(panel).getByText("Oranlar toplam 4 tavsiye üzerinden hesaplanır.")).toBeVisible();
  });

  it("veri yoksa ortalamayı 0, yüzdeyi %0 göstermez; boş durumları açıklar", async () => {
    stats = { gameId, ratings: { ratingCount: 0, averageRating: null, distribution: buckets(Array(10).fill(0)) }, reviews: { activeCount: 0 }, recommendations: { recommendedCount: 0, notRecommendedCount: 0, totalCount: 0, recommendedPercentage: null, notRecommendedPercentage: null } };
    render(wrap(<ReviewStatisticsPage gameId={gameId} />));
    expect(await screen.findByText("Bu oyun için henüz puan, inceleme veya tavsiye yok.")).toBeVisible();
    expect(screen.getByText("Henüz puan yok")).toBeVisible();
    expect(screen.getByText("Henüz tavsiye yok")).toBeVisible();
    expect(screen.getByText("Henüz tavsiye verilmedi.")).toBeVisible();
    expect(screen.getByText("Henüz puan verilmedi; tüm kovalar boş.")).toBeVisible();
    expect(screen.queryByText("0%")).toBeNull();
    expect(screen.queryByText(/^0 \/ 10$/)).toBeNull();
    expect(within(screen.getByRole("list", { name: "Puan dağılımı" })).getAllByRole("listitem")).toHaveLength(10);
  });

  it("gerçek %0 yüzdesini veri yok durumundan ayırır", async () => {
    stats = { gameId, ratings: { ratingCount: 0, averageRating: null, distribution: buckets(Array(10).fill(0)) }, reviews: { activeCount: 0 }, recommendations: { recommendedCount: 0, notRecommendedCount: 5, totalCount: 5, recommendedPercentage: 0, notRecommendedPercentage: 100 } };
    render(wrap(<ReviewStatisticsPage gameId={gameId} />));
    const panel = (await screen.findByRole("heading", { name: "Tavsiye dağılımı" })).closest("section") as HTMLElement;
    expect(within(panel).getByText("0%")).toBeVisible();
    expect(within(panel).getByText("100%")).toBeVisible();
  });

  it("puan veren ile aktif inceleme sayısını karıştırmaz (puan var, metin yok)", async () => {
    stats = { gameId, ratings: { ratingCount: 9, averageRating: 8, distribution: buckets([0, 0, 0, 0, 0, 0, 0, 9, 0, 0]) }, reviews: { activeCount: 0 }, recommendations: { recommendedCount: 0, notRecommendedCount: 0, totalCount: 0, recommendedPercentage: null, notRecommendedPercentage: null } };
    render(wrap(<ReviewStatisticsPage gameId={gameId} />));
    const overview = (await screen.findByText("Aktif inceleme")).closest("dl") as HTMLElement;
    expect(within(overview).getByText("Puan veren kullanıcı").nextElementSibling).toHaveTextContent("9");
    expect(within(overview).getByText("Aktif inceleme").nextElementSibling).toHaveTextContent("0");
  });

  it("hata durumunda hata ve tekrar dene gösterir", async () => {
    stats = new ApiHttpError(503);
    render(wrap(<ReviewStatisticsPage gameId={gameId} />));
    expect(await screen.findByText("Servis geçici olarak kullanılamıyor. Biraz sonra tekrar dene.")).toBeVisible();
    expect(screen.getByRole("button", { name: "Tekrar dene" })).toBeVisible();
  });

  it("oyun incelemelerine dönüş bağlantısı sunar", async () => {
    render(wrap(<ReviewStatisticsPage gameId={gameId} />));
    expect(await screen.findByRole("link", { name: "Oyun incelemelerine dön" })).toHaveAttribute("href", `/reviews/games/${gameId}`);
  });

  it("İngilizce metinleri kullanır ve sayıları dile göre biçimlendirir", async () => {
    render(wrap(<ReviewStatisticsPage gameId={gameId} />, "en"));
    expect(await screen.findByRole("heading", { level: 1, name: "Review statistics" })).toBeVisible();
    expect(screen.getByText("5.5 / 10")).toBeVisible();
  });
});
