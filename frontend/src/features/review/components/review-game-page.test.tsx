import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import en from "@/shared/lib/i18n/messages/en.json";
import { loadReviewResource, reviewMutations } from "../services/review-api";
import type { AuthorCard, FeaturedReviewsResponse, GameReviewStatistics, ReviewResource } from "../types/review";
import { ReviewGamePage } from "./review-game-page";

const mocks = vi.hoisted(() => ({ status: "authenticated", query: "", replace: vi.fn(), push: vi.fn() }));
vi.mock("@/features/auth", () => ({ useRequireSession: () => ({ status: mocks.status, me: mocks.status === "authenticated" ? { accountId: "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66" } : null }) }));
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(mocks.query),
  usePathname: () => "/tr/incelemeler/oyun/0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66",
  useRouter: () => ({ replace: mocks.push, push: mocks.push })
}));
vi.mock("@/shared/lib/i18n/navigation", () => ({
  useRouter: () => ({ push: mocks.push, replace: mocks.push }),
  Link: ({ href, children, ...props }: { href: string | { pathname: string; params?: Record<string, string> }; children: ReactNode }) => {
    const target = typeof href === "string" ? href : Object.entries(href.params ?? {}).reduce((path, [key, value]) => path.replace(`[${key}]`, value), href.pathname);
    return <a href={target} {...props}>{children}</a>;
  }
}));
vi.mock("next/image", () => ({ default: () => <span data-testid="cover-image" /> }));
vi.mock("../services/review-api", () => ({
  loadReviewResource: vi.fn(),
  reviewApi: { review: vi.fn() },
  reviewMutations: { rate: vi.fn(), recommend: vi.fn(), like: vi.fn(), unlike: vi.fn(), createReview: vi.fn(), updateReview: vi.fn(), deleteReview: vi.fn() },
  findOwnReview: vi.fn(),
  OWN_REVIEW_SCAN_PAGES: 20
}));

const gameId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const load = vi.mocked(loadReviewResource);
const rate = vi.mocked(reviewMutations.rate);
const recommend = vi.mocked(reviewMutations.recommend);
const like = vi.mocked(reviewMutations.like);

const stats: GameReviewStatistics = {
  gameId,
  ratings: { ratingCount: 120, averageRating: 7.25, distribution: Array.from({ length: 10 }, (_, index) => ({ rating: index + 1, userCount: index })) },
  reviews: { activeCount: 2 },
  recommendations: { recommendedCount: 3, notRecommendedCount: 1, totalCount: 4, recommendedPercentage: 75, notRecommendedPercentage: 25 }
};
const featured: FeaturedReviewsResponse = {
  items: [
    { review: { id: "0198a3c2-1111-7d44-9a55-3f1c2b4d5e66", gameId, body: "Başkasının <b>kalın</b> yorumu", authorUserId: "0198a3c2-0000-7d44-9a55-3f1c2b4d5e02", ownedByActor: false, status: "VISIBLE", version: 0, createdAt: "2026-11-01T12:00:00Z", updatedAt: "2026-11-01T12:00:00Z" }, likeCount: 10, likedByActor: false },
    { review: { id: "0198a3c2-2222-7d44-9a55-3f1c2b4d5e66", gameId, body: "Benim yorumum", authorUserId: "0198a3c2-0000-7d44-9a55-3f1c2b4d5e01", ownedByActor: true, status: "VISIBLE", version: 2, createdAt: "2026-11-01T12:00:00Z", updatedAt: "2026-11-02T12:00:00Z" }, likeCount: 4, likedByActor: false }
  ],
  page: 0,
  size: 20,
  totalElements: 45,
  totalPages: 3
};

type World = { authors: AuthorCard[]; ownRating: unknown; ownRecommendation: unknown; ownReview: unknown; featured: FeaturedReviewsResponse; statistics: GameReviewStatistics };
let world: World;

function wrap(children: ReactNode, locale: "tr" | "en" = "tr") {
  return <NextIntlClientProvider locale={locale} messages={locale === "tr" ? tr : en}>{children}</NextIntlClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
  mocks.status = "authenticated";
  mocks.query = "";
  world = { authors: [{ userId: "0198a3c2-0000-7d44-9a55-3f1c2b4d5e02", username: "oyuncu", displayName: "Oyuncu <b>Adı</b>", avatarPath: "/api/v1/user/media/0123456789abcdef0123456789abcdef-s.jpg" }], ownRating: null, ownRecommendation: null, ownReview: null, featured, statistics: stats };
  load.mockImplementation(async (resource: ReviewResource) => {
    switch (resource.kind) {
      case "gameCard": return { gameId, title: "Test Oyunu", coverUrl: null };
      case "statistics": return world.statistics;
      case "ownRating": return world.ownRating;
      case "ownRecommendation": return world.ownRecommendation;
      case "featured": return world.featured;
      case "ownReview": return world.ownReview;
      case "authorCards": return world.authors.filter((card) => resource.userIds.includes(card.userId));
      default: throw new Error(`beklenmeyen kaynak ${resource.kind}`);
    }
  });
});

async function open() {
  render(wrap(<ReviewGamePage gameId={gameId} />));
  await screen.findByRole("heading", { level: 1, name: "Test Oyunu" });
}

describe("Oyun inceleme ekranı", () => {
  it("oyunun gerçek adını, özet sayıları ve bağlantıları gösterir", async () => {
    await open();
    expect(screen.getByRole("link", { name: "İnceleme yaz" })).toHaveAttribute("href", `/reviews/games/${gameId}/write`);
    expect(screen.getByRole("link", { name: "İstatistikler" })).toHaveAttribute("href", `/reviews/games/${gameId}/statistics`);
    expect(screen.getByRole("link", { name: "Oyun sayfasını aç" })).toHaveAttribute("href", `/games/${gameId}`);
    expect(await screen.findByText("7,25 / 10")).toBeVisible();
    expect(screen.getByText("120")).toBeVisible();
    expect(screen.getByText("75%")).toBeVisible();
    expect(screen.getByText("3 olumlu · 1 olumsuz")).toBeVisible();
  });

  it("puan ve inceleme yoksa ortalamayı 0/10, yüzdeyi %0 göstermez", async () => {
    world.statistics = { gameId, ratings: { ratingCount: 0, averageRating: null, distribution: [] }, reviews: { activeCount: 0 }, recommendations: { recommendedCount: 0, notRecommendedCount: 0, totalCount: 0, recommendedPercentage: null, notRecommendedPercentage: null } };
    await open();
    expect(await screen.findByText("Henüz puan yok")).toBeVisible();
    expect(screen.getByText("Henüz tavsiye yok")).toBeVisible();
    expect(screen.queryByText(/0 \/ 10/)).toBeNull();
    expect(screen.queryByText("0%")).toBeNull();
  });

  it("Game Service kartı alamazsa Review verisini gizlemez", async () => {
    load.mockImplementation(async (resource: ReviewResource) => {
      if (resource.kind === "gameCard") throw new ApiHttpError(503);
      if (resource.kind === "statistics") return stats;
      if (resource.kind === "featured") return featured;
      return null;
    });
    render(wrap(<ReviewGamePage gameId={gameId} />));
    expect(await screen.findByText(/Oyun bilgisi şu anda alınamıyor/)).toBeVisible();
    expect(await screen.findByText("Benim yorumum")).toBeVisible();
  });
});

describe("Yazar satırı", () => {
  it("başkasının incelemesinde User kartından adı, avatarı ve profil bağlantısını düz metin olarak gösterir; kendi incelemesinde göstermez", async () => {
    await open();
    const link = await screen.findByRole("link", { name: "Oyuncu <b>Adı</b>" });
    expect(link).toHaveAttribute("href", "/players/0198a3c2-0000-7d44-9a55-3f1c2b4d5e02");
    expect(link.querySelector("img")).toHaveAttribute("src", "/api/v1/user/media/0123456789abcdef0123456789abcdef-s.jpg");
    expect(link.querySelector("b")).toBeNull();
    expect(screen.queryByText("Bir oyuncu")).toBeNull();
    expect(load.mock.calls.filter(([resource]) => resource.kind === "authorCards")).toHaveLength(1);
    expect(load.mock.calls.find(([resource]) => resource.kind === "authorCards")?.[0]).toEqual({ kind: "authorCards", userIds: ["0198a3c2-0000-7d44-9a55-3f1c2b4d5e02"] });
  });

  it("kartı olmayan yazar için 'Bir oyuncu' gösterir ve profil bağlantısı vermez; inceleme gizlenmez", async () => {
    world.authors = [];
    await open();
    expect(await screen.findByText("Bir oyuncu")).toBeVisible();
    expect(screen.queryByRole("link", { name: /Oyuncu/ })).toBeNull();
    expect(await screen.findByText("Başkasının <b>kalın</b> yorumu")).toBeVisible();
  });

  it("yalnız kendi incelemesi varsa yazar kartı istenmez", async () => {
    world.featured = { ...featured, items: [featured.items[1]], totalElements: 1, totalPages: 1 };
    await open();
    await screen.findByText("Benim yorumum");
    expect(load.mock.calls.some(([resource]) => resource.kind === "authorCards")).toBe(false);
  });
});

describe("Kendi puanım", () => {
  it("kayıt yoksa 'puan vermedin' der; hiçbir seçenek işaretli değildir (0 puan yok)", async () => {
    await open();
    expect(await screen.findByText("Henüz puan vermedin.")).toBeVisible();
    const group = screen.getByRole("radiogroup", { name: "Oyun puanı" });
    expect(within(group).getAllByRole("radio")).toHaveLength(10);
    expect(within(group).getAllByRole("radio").filter((radio) => radio.getAttribute("aria-checked") === "true")).toHaveLength(0);
    expect(screen.getByRole("button", { name: "Puanı kaydet" })).toBeDisabled();
  });

  it("mevcut puanı gösterir; değiştirmeden kaydetmeye izin vermez", async () => {
    world.ownRating = { gameId, rating: 8, version: 1, createdAt: "", updatedAt: "" };
    await open();
    expect(await screen.findByText("Kayıtlı puanın: 8 / 10")).toBeVisible();
    expect(screen.getByRole("radio", { name: "8 puan" })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("button", { name: "Puanı kaydet" })).toBeDisabled();
  });

  it("1–10 sınırında kalır, ok tuşlarıyla seçilir ve sunucu yanıtıyla kaydeder", async () => {
    rate.mockResolvedValue({ gameId, rating: 10, version: 0, createdAt: "", updatedAt: "" });
    await open();
    await screen.findByText("Henüz puan vermedin.");
    const first = screen.getByRole("radio", { name: "1 puan" });
    first.focus();
    fireEvent.keyDown(first, { key: "ArrowLeft" });
    expect(first).toHaveAttribute("aria-checked", "true");
    fireEvent.keyDown(first, { key: "End" });
    expect(screen.getByRole("radio", { name: "10 puan" })).toHaveAttribute("aria-checked", "true");
    fireEvent.keyDown(screen.getByRole("radio", { name: "10 puan" }), { key: "ArrowRight" });
    expect(screen.getByRole("radio", { name: "10 puan" })).toHaveAttribute("aria-checked", "true");
    // Tek sekme durağı: yalnız seçili düğme tabIndex=0.
    expect(screen.getAllByRole("radio", { name: /puan$/ }).filter((radio) => radio.tabIndex === 0)).toHaveLength(1);

    fireEvent.click(screen.getByRole("button", { name: "Puanı kaydet" }));
    expect(await screen.findByText("Puanın kaydedildi.")).toBeVisible();
    expect(rate).toHaveBeenCalledExactlyOnceWith(gameId, 10, expect.any(AbortSignal));
    expect(screen.getByText("Kayıtlı puanın: 10 / 10")).toBeVisible();
    expect(recommend).not.toHaveBeenCalled();
    // Özet sayılar sunucudan yeniden okunur.
    await waitFor(() => expect(load.mock.calls.filter(([resource]) => resource.kind === "statistics").length).toBeGreaterThan(1));
  });

  it("kayıt sırasında düğme kapanır ve çift gönderim olmaz", async () => {
    let finish: (value: unknown) => void = () => undefined;
    rate.mockImplementation(() => new Promise((resolve) => { finish = resolve as (value: unknown) => void; }) as never);
    await open();
    await screen.findByText("Henüz puan vermedin.");
    fireEvent.click(screen.getByRole("radio", { name: "6 puan" }));
    const save = screen.getByRole("button", { name: "Puanı kaydet" });
    fireEvent.click(save);
    fireEvent.click(save);
    const busy = await screen.findByRole("button", { name: "Kaydediliyor…" });
    expect(busy).toBeDisabled();
    expect(rate).toHaveBeenCalledTimes(1);
    finish({ gameId, rating: 6, version: 0, createdAt: "", updatedAt: "" });
    expect(await screen.findByText("Puanın kaydedildi.")).toBeVisible();
  });

  it("hata durumunda seçimi korur ve başarı mesajı göstermez", async () => {
    rate.mockRejectedValue(new ApiHttpError(503));
    await open();
    await screen.findByText("Henüz puan vermedin.");
    fireEvent.click(screen.getByRole("radio", { name: "4 puan" }));
    fireEvent.click(screen.getByRole("button", { name: "Puanı kaydet" }));
    expect(await screen.findByText("Servis geçici olarak kullanılamıyor. Biraz sonra tekrar dene.")).toBeVisible();
    expect(screen.getByRole("radio", { name: "4 puan" })).toHaveAttribute("aria-checked", "true");
    expect(screen.queryByText("Puanın kaydedildi.")).toBeNull();
    expect(screen.getByRole("button", { name: "Puanı kaydet" })).toBeEnabled();
  });

  it("oturum süresi dolduysa giriş bağlantısı sunar", async () => {
    rate.mockRejectedValue(new ApiHttpError(401));
    await open();
    await screen.findByText("Henüz puan vermedin.");
    fireEvent.click(screen.getByRole("radio", { name: "9 puan" }));
    fireEvent.click(screen.getByRole("button", { name: "Puanı kaydet" }));
    expect(await screen.findByRole("link", { name: "Giriş yap" })).toBeVisible();
  });
});

describe("Kendi tavsiyem", () => {
  it("kayıt yokluğu 'önermiyorum' değildir: hiçbir seçenek işaretli görünmez", async () => {
    await open();
    expect(await screen.findByText("Henüz tavsiye vermedin.")).toBeVisible();
    expect(screen.getByRole("radio", { name: "Öneriyorum" })).toHaveAttribute("aria-checked", "false");
    expect(screen.getByRole("radio", { name: "Önermiyorum" })).toHaveAttribute("aria-checked", "false");
  });

  it("false değerini kaydeder ve puandan bağımsız çalışır", async () => {
    recommend.mockResolvedValue({ gameId, recommended: false, version: 0, createdAt: "", updatedAt: "" });
    await open();
    await screen.findByText("Henüz tavsiye vermedin.");
    fireEvent.click(screen.getByRole("radio", { name: "Önermiyorum" }));
    fireEvent.click(screen.getByRole("button", { name: "Tavsiyeyi kaydet" }));
    expect(await screen.findByText("Tavsiyen kaydedildi.")).toBeVisible();
    expect(recommend).toHaveBeenCalledExactlyOnceWith(gameId, false, expect.any(AbortSignal));
    expect(rate).not.toHaveBeenCalled();
    expect(screen.getByText("Kayıtlı tavsiyen: Önermiyorum")).toBeVisible();
  });

  it("kayıtlı olumsuz tavsiyeyi olumsuz olarak gösterir (false ≠ kayıt yok)", async () => {
    world.ownRecommendation = { gameId, recommended: false, version: 1, createdAt: "", updatedAt: "" };
    await open();
    expect(await screen.findByText("Kayıtlı tavsiyen: Önermiyorum")).toBeVisible();
    expect(screen.getByRole("radio", { name: "Önermiyorum" })).toHaveAttribute("aria-checked", "true");
  });
});

describe("Öne çıkan incelemeler", () => {
  it("inceleme metnini düz metin olarak çizer (HTML çalıştırılmaz)", async () => {
    await open();
    const text = await screen.findByText("Başkasının <b>kalın</b> yorumu");
    expect(text.querySelector("b")).toBeNull();
    expect(text.tagName).toBe("P");
  });

  it("gerçek toplam sayıyı gösterir ve sahip rozetini yalnız sahibin kartında gösterir", async () => {
    await open();
    expect(await screen.findByText("45 inceleme")).toBeVisible();
    const cards = screen.getAllByRole("article");
    expect(cards).toHaveLength(2);
    expect(within(cards[0]).queryByText("Senin incelemen")).toBeNull();
    expect(within(cards[1]).getByText("Senin incelemen")).toBeVisible();
    expect(within(cards[1]).getByText(/Düzenlendi/)).toBeVisible();
    expect(within(cards[0]).queryByText(/Düzenlendi/)).toBeNull();
  });

  it("başkasının kartında 'Raporla' vardır; kendi kartında yoktur", async () => {
    await open();
    const cards = await screen.findAllByRole("article");
    expect(within(cards[0]).getByRole("button", { name: "Raporla" })).toBeVisible();
    expect(within(cards[1]).queryByRole("button", { name: "Raporla" })).toBeNull();
  });

  it("kendi incelemende beğeni düğmesi yoktur; başkasınınkinde vardır", async () => {
    await open();
    const cards = await screen.findAllByRole("article");
    expect(within(cards[0]).getByRole("button", { name: /Beğen/ })).toBeVisible();
    expect(within(cards[1]).queryByRole("button", { name: /Beğen/ })).toBeNull();
    expect(within(cards[1]).getByText(/kendi incelemeni beğenemezsin/)).toBeVisible();
    expect(within(cards[1]).getByRole("link", { name: "Aç ve yönet" })).toHaveAttribute("href", "/reviews/0198a3c2-2222-7d44-9a55-3f1c2b4d5e66");
  });

  it("sunucunun likedByActor değerini ilk durumda kullanır: beğenilmişse kaldırma sunar", async () => {
    world.featured = { ...featured, items: [{ ...featured.items[0], likedByActor: true }, featured.items[1]] };
    await open();
    const card = (await screen.findAllByRole("article"))[0];
    expect(within(card).getByRole("button", { name: /Beğeniyi kaldır/ })).toHaveAttribute("aria-pressed", "true");
    expect(within(card).queryByRole("button", { name: /^Beğen(?!iyi)/ })).toBeNull();
  });

  it("kendi incelemen varsa 'yaz' yerine 'incelemeni aç' bağlantısı sunar", async () => {
    world.ownReview = featured.items[1].review;
    await open();
    const link = await screen.findByRole("link", { name: "İncelemeni aç" });
    expect(link).toHaveAttribute("href", "/reviews/0198a3c2-2222-7d44-9a55-3f1c2b4d5e66");
    expect(screen.queryByRole("link", { name: "İnceleme yaz" })).toBeNull();
  });

  it("beğenmeyi sunucu yanıtıyla günceller", async () => {
    like.mockResolvedValue({ reviewId: featured.items[0].review.id, likeCount: 11, likedByActor: true });
    await open();
    const card = (await screen.findAllByRole("article"))[0];
    const button = within(card).getByRole("button", { name: /Beğen/ });
    expect(button).toHaveAttribute("aria-pressed", "false");
    fireEvent.click(button);
    await waitFor(() => expect(within(card).getByRole("button", { name: /Beğeniyi kaldır/ })).toHaveAttribute("aria-pressed", "true"));
    expect(within(card).getByText("11")).toBeVisible();
    expect(like).toHaveBeenCalledExactlyOnceWith(featured.items[0].review.id, expect.any(AbortSignal));
  });

  it("beğeni kotası aşılınca Retry-After süresince düğmeyi kapatır, otomatik tekrar denemez", async () => {
    like.mockRejectedValue(new ApiHttpError(429, { retryAfterSeconds: 30 }));
    await open();
    const card = (await screen.findAllByRole("article"))[0];
    fireEvent.click(within(card).getByRole("button", { name: /Beğen/ }));
    expect(await within(card).findByText(/30 saniye sonra tekrar deneyebilirsin/)).toBeVisible();
    expect(within(card).getByRole("button", { name: /Beğen/ })).toBeDisabled();
    expect(like).toHaveBeenCalledTimes(1);
  });

  it("asgari beğeni filtresini URL sorgusuna yazar ve sayfayı sıfırlar", async () => {
    mocks.query = "page=2";
    await open();
    await screen.findByText("45 inceleme");
    fireEvent.change(screen.getByLabelText("En az beğeni"), { target: { value: "5" } });
    fireEvent.click(screen.getByRole("button", { name: "Uygula" }));
    expect(mocks.push).toHaveBeenCalledWith("/tr/incelemeler/oyun/0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66?minLikes=5", { scroll: false });
  });

  it("sayfalamada bir sonraki sayfayı URL'ye yazar", async () => {
    await open();
    await screen.findByText("45 inceleme");
    fireEvent.click(screen.getByRole("button", { name: "Sonraki" }));
    expect(mocks.push).toHaveBeenCalledWith("/tr/incelemeler/oyun/0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66?page=1", { scroll: false });
  });

  it("URL sorgusundaki sayfa, boyut ve filtreyi istekte kullanır; geçersiz değerleri düzeltir", async () => {
    mocks.query = "page=1&size=50&minLikes=3";
    await open();
    await screen.findByText("45 inceleme");
    expect(load).toHaveBeenCalledWith({ kind: "featured", gameId, query: { page: 1, size: 50, minLikes: 3 } }, expect.any(AbortSignal));
  });

  it("geçersiz sorgu değerleri sunucuya taşınmaz", async () => {
    // Negatif/metin değerler varsayılana düşer; sınırı aşan boyut izinli en büyük değere (50) çekilir.
    mocks.query = "page=-5&size=9999&minLikes=abc";
    await open();
    await screen.findByText("45 inceleme");
    expect(load).toHaveBeenCalledWith({ kind: "featured", gameId, query: { page: 0, size: 50, minLikes: 0 } }, expect.any(AbortSignal));
  });

  it("inceleme yoksa nedeni ve sonraki adımı açıklar", async () => {
    world.featured = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
    await open();
    expect(await screen.findByText("Bu oyun için henüz inceleme yok")).toBeVisible();
    expect(screen.getByText("İlk incelemeyi sen yazabilirsin.")).toBeVisible();
  });

  it("filtreye uyan inceleme yoksa filtre boş durumunu gösterir", async () => {
    mocks.query = "minLikes=99";
    world.featured = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
    await open();
    expect(await screen.findByText("Bu filtreyle inceleme yok")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Filtreyi temizle" }));
    expect(mocks.push).toHaveBeenCalledWith("/tr/incelemeler/oyun/0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66", { scroll: false });
  });

  it("liste yüklenemezse hata ve 'Tekrar dene' sunar; sayfa çökmez", async () => {
    load.mockImplementation(async (resource: ReviewResource) => {
      if (resource.kind === "featured") throw new ApiHttpError(500);
      if (resource.kind === "gameCard") return { gameId, title: "Test Oyunu", coverUrl: null };
      if (resource.kind === "statistics") return stats;
      return null;
    });
    render(wrap(<ReviewGamePage gameId={gameId} />));
    expect(await screen.findByText(/Sunucu işlemi tamamlayamadı/)).toBeVisible();
    expect(screen.getAllByRole("button", { name: "Tekrar dene" }).length).toBeGreaterThan(0);
  });
});

describe("Oturum ve dil", () => {
  it("oturum yokken içerik çizilmez ve veri istenmez", () => {
    mocks.status = "guest";
    render(wrap(<ReviewGamePage gameId={gameId} />));
    expect(screen.getByRole("status", { name: "Yükleniyor" })).toBeVisible();
    expect(load).not.toHaveBeenCalled();
  });

  it("İngilizce metinleri kullanır", async () => {
    render(wrap(<ReviewGamePage gameId={gameId} />, "en"));
    expect(await screen.findByRole("link", { name: "Write a review" })).toBeVisible();
    expect(await screen.findByText("You have not rated this game yet.")).toBeVisible();
    expect(screen.getByRole("radio", { name: "I recommend it" })).toBeVisible();
  });
});
