import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError, apiRequest } from "@/shared/lib/api/client";
import { featuredQueryString, gameLookup, loadReviewResource, reviewApi, reviewMutations } from "./review-api";
import type { ReviewResponse } from "../types/review";

vi.mock("@/shared/lib/api/client", async (original) => ({ ...(await original<typeof import("@/shared/lib/api/client")>()), apiRequest: vi.fn() }));
const request = vi.mocked(apiRequest);
const gameId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const reviewId = "0198a3c2-cccc-7d44-9a55-3f1c2b4d5e66";
const signal = new AbortController().signal;
const review: ReviewResponse = { id: reviewId, gameId, body: "Metin", ownedByActor: true, version: 3, createdAt: "2026-11-01T12:00:00Z", updatedAt: "2026-11-01T12:00:00Z" };

beforeEach(() => {
  document.cookie = "XSRF-TOKEN=review-test-csrf; path=/";
});
afterEach(() => {
  vi.resetAllMocks();
  document.cookie = "XSRF-TOKEN=; max-age=0; path=/";
});

function lastCall() {
  const [path, init] = request.mock.calls.at(-1)!;
  return { path, init: init as RequestInit & { headers: Record<string, string> } };
}

describe("Review okuma uçları (Gateway üzerinden)", () => {
  it("kendi puanını okur; kayıt yokluğu (404) null'dır, 0 değildir", async () => {
    request.mockResolvedValueOnce({ gameId, rating: 7, version: 0, createdAt: "", updatedAt: "" });
    expect(await reviewApi.ownRating(gameId, signal)).toMatchObject({ rating: 7 });
    expect(lastCall().path).toBe(`/api/v1/review/games/${gameId}/rating`);
    request.mockRejectedValueOnce(new ApiHttpError(404));
    expect(await reviewApi.ownRating(gameId, signal)).toBeNull();
  });

  it("kendi tavsiyesini okur; 404 null'dır, false değildir", async () => {
    request.mockRejectedValueOnce(new ApiHttpError(404));
    expect(await reviewApi.ownRecommendation(gameId, signal)).toBeNull();
    request.mockResolvedValueOnce({ gameId, recommended: false, version: 1, createdAt: "", updatedAt: "" });
    expect(await reviewApi.ownRecommendation(gameId, signal)).toMatchObject({ recommended: false });
    expect(lastCall().path).toBe(`/api/v1/review/games/${gameId}/recommendation`);
  });

  it("404 dışındaki hatayı yutmaz", async () => {
    request.mockRejectedValueOnce(new ApiHttpError(503));
    await expect(reviewApi.ownRating(gameId, signal)).rejects.toMatchObject({ status: 503 });
    request.mockRejectedValueOnce(new ApiHttpError(403));
    await expect(reviewApi.ownRecommendation(gameId, signal)).rejects.toMatchObject({ status: 403 });
  });

  it("istatistik, tekil özet, inceleme ve beğeni durumunu doğru yoldan okur", async () => {
    request.mockResolvedValue({});
    await reviewApi.statistics(gameId, signal);
    expect(lastCall().path).toBe(`/api/v1/review/games/${gameId}/statistics`);
    await reviewApi.ratingSummary(gameId, signal);
    expect(lastCall().path).toBe(`/api/v1/review/games/${gameId}/rating-summary`);
    await reviewApi.review(reviewId, signal);
    expect(lastCall().path).toBe(`/api/v1/review/reviews/${reviewId}`);
    await reviewApi.like(reviewId, signal);
    expect(lastCall().path).toBe(`/api/v1/review/reviews/${reviewId}/like`);
    expect(lastCall().init).toMatchObject({ cache: "no-store", signal });
  });

  it("öne çıkanlar sorgusunu aralık dışı değerlerden arındırır", async () => {
    expect(featuredQueryString({ page: 2, size: 10, minLikes: 4 })).toBe("page=2&size=10&minLikes=4");
    expect(featuredQueryString({ page: -1, size: 500, minLikes: -3 })).toBe("page=0&size=20&minLikes=0");
    expect(featuredQueryString({ page: 1.5, size: 20, minLikes: 0.5 })).toBe("page=0&size=20&minLikes=0");
    request.mockResolvedValue({ items: [], page: 1, size: 10, totalElements: 0, totalPages: 0 });
    await reviewApi.featured(gameId, { page: 1, size: 10, minLikes: 2 }, signal);
    expect(lastCall().path).toBe(`/api/v1/review/games/${gameId}/reviews/featured?page=1&size=10&minLikes=2`);
  });

  it("geçersiz kimlikle ağa çıkmaz", async () => {
    await expect(Promise.resolve().then(() => reviewApi.review("../admin", signal))).rejects.toMatchObject({ status: 400 });
    await expect(Promise.resolve().then(() => reviewApi.statistics("730", signal))).rejects.toMatchObject({ status: 400 });
    expect(request).not.toHaveBeenCalled();
  });
});

describe("kendi incelemelerim", () => {
  it("oyundaki kendi incelemeni okur; kayıt yokluğu (404) null'dır", async () => {
    request.mockResolvedValueOnce(review);
    expect(await reviewApi.ownReview(gameId, signal)).toMatchObject({ id: reviewId, version: 3 });
    expect(lastCall().path).toBe(`/api/v1/review/games/${gameId}/reviews/mine`);
    request.mockRejectedValueOnce(new ApiHttpError(404));
    expect(await reviewApi.ownReview(gameId, signal)).toBeNull();
    request.mockRejectedValueOnce(new ApiHttpError(503));
    await expect(reviewApi.ownReview(gameId, signal)).rejects.toMatchObject({ status: 503 });
  });

  it("tüm oyunlardaki kendi incelemelerini sayfa ve boyut sınırlarıyla okur", async () => {
    request.mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    await reviewApi.myReviews(2, 10, signal);
    expect(lastCall().path).toBe("/api/v1/review/reviews/mine?page=2&size=10");
    await reviewApi.myReviews(-1, 500, signal);
    expect(lastCall().path).toBe("/api/v1/review/reviews/mine?page=0&size=20");
    expect(lastCall().init).toMatchObject({ cache: "no-store", signal });
  });

  it("geçersiz oyun kimliğiyle ağa çıkmaz", async () => {
    await expect(Promise.resolve().then(() => reviewApi.ownReview("730", signal))).rejects.toMatchObject({ status: 400 });
    expect(request).not.toHaveBeenCalled();
  });
});

describe("raporlama", () => {
  it("raporu gerekçe ve temizlenmiş açıklamayla POST eder", async () => {
    request.mockResolvedValue({ id: reviewId, reviewId, reason: "SPAM", detail: "Reklam", status: "RECEIVED", createdAt: "" });
    await reviewMutations.reportReview(reviewId, "SPAM", "  Reklam  ", signal);
    const { path, init } = lastCall();
    expect(path).toBe(`/api/v1/review/reviews/${reviewId}/reports`);
    expect(init.method).toBe("POST");
    expect(init.headers["X-XSRF-TOKEN"]).toBe("review-test-csrf");
    expect(JSON.parse(init.body as string)).toEqual({ reason: "SPAM", detail: "Reklam" });
  });

  it("boş açıklamada detail göndermez", async () => {
    request.mockResolvedValue({});
    await reviewMutations.reportReview(reviewId, "OTHER", "   ", signal);
    expect(JSON.parse(lastCall().init.body as string)).toEqual({ reason: "OTHER" });
  });

  it("geçersiz gerekçe veya uzun/denetim karakterli açıklamayı ağa göndermez", () => {
    expect(() => reviewMutations.reportReview(reviewId, "BILINMEYEN" as never, "", signal)).toThrow(ApiHttpError);
    expect(() => reviewMutations.reportReview(reviewId, "SPAM", "x".repeat(501), signal)).toThrow(ApiHttpError);
    expect(() => reviewMutations.reportReview(reviewId, "SPAM", "a\u0000b", signal)).toThrow(ApiHttpError);
    expect(() => reviewMutations.reportReview("730", "SPAM", "", signal)).toThrow(ApiHttpError);
    expect(request).not.toHaveBeenCalled();
  });

  it("mükerrer raporu (409) ve kotayı (429) otomatik tekrarlamaz", async () => {
    request.mockRejectedValue(new ApiHttpError(409));
    await expect(reviewMutations.reportReview(reviewId, "SPAM", "", signal)).rejects.toMatchObject({ status: 409 });
    request.mockRejectedValue(new ApiHttpError(429, { retryAfterSeconds: 20 }));
    await expect(reviewMutations.reportReview(reviewId, "SPAM", "", signal)).rejects.toMatchObject({ status: 429, retryAfterSeconds: 20 });
    expect(request).toHaveBeenCalledTimes(2);
  });

  it("kendi raporunu okur; 404 null'dır", async () => {
    request.mockResolvedValueOnce({ id: reviewId, reviewId, reason: "SPAM", detail: null, status: "RECEIVED", createdAt: "" });
    expect(await reviewApi.ownReport(reviewId, signal)).toMatchObject({ reason: "SPAM" });
    expect(lastCall().path).toBe(`/api/v1/review/reviews/${reviewId}/reports/mine`);
    request.mockRejectedValueOnce(new ApiHttpError(404));
    expect(await reviewApi.ownReport(reviewId, signal)).toBeNull();
    await loadReviewResource({ kind: "ownReport", reviewId }, signal);
    expect(lastCall().path).toBe(`/api/v1/review/reviews/${reviewId}/reports/mine`);
  });
});

describe("toplu puan özeti (REV-12)", () => {
  it("oyun başına istek atmaz; tek POST ile en çok 50 benzersiz kimlik gönderir", async () => {
    request.mockResolvedValue({ items: [] });
    const ids = Array.from({ length: 70 }, (_, index) => `0198a3c2-bbbb-7d44-9a55-${String(index).padStart(12, "0")}`);
    await reviewApi.ratingSummaries([...ids, ids[0], "geçersiz"], signal);
    expect(request).toHaveBeenCalledTimes(1);
    const { path, init } = lastCall();
    expect(path).toBe("/api/v1/review/games/rating-summaries");
    expect(init.method).toBe("POST");
    expect(init.headers["X-XSRF-TOKEN"]).toBe("review-test-csrf");
    expect(JSON.parse(init.body as string).gameIds).toEqual(ids.slice(0, 50));
  });

  it("boş listede istek atmaz", async () => {
    expect(await reviewApi.ratingSummaries([], signal)).toEqual({ items: [] });
    expect(await reviewApi.ratingSummaries(["x"], signal)).toEqual({ items: [] });
    expect(request).not.toHaveBeenCalled();
  });
});

describe("Game Service (yalnız okuma)", () => {
  it("oyun kartını herkese açık toplu uçtan, çerezsiz okur", async () => {
    request.mockResolvedValue({ items: [{ gameId, title: "Oyun", coverUrl: null }], unavailable: [] });
    expect(await gameLookup.card(gameId, signal)).toMatchObject({ title: "Oyun" });
    const { path, init } = lastCall();
    expect(path).toBe("/api/v1/game/games/batch");
    expect(init).toMatchObject({ method: "POST", credentials: "omit" });
    expect(JSON.parse(init.body as string)).toEqual({ gameIds: [gameId] });
  });

  it("toplu kartı tek istekle, en çok 50 benzersiz kimlikle ve çerezsiz okur", async () => {
    const ids = Array.from({ length: 70 }, (_, index) => `0198a3c2-bbbb-7d44-9a55-${String(index).padStart(12, "0")}`);
    request.mockResolvedValue({ items: [{ gameId: ids[0], title: "A", coverUrl: null }], unavailable: [] });
    expect(await gameLookup.cards([...ids, ids[0], "x"], signal)).toHaveLength(1);
    expect(request).toHaveBeenCalledTimes(1);
    const { path, init } = lastCall();
    expect(path).toBe("/api/v1/game/games/batch");
    expect(init).toMatchObject({ method: "POST", credentials: "omit" });
    expect(JSON.parse(init.body as string).gameIds).toEqual(ids.slice(0, 50));
    request.mockClear();
    expect(await gameLookup.cards([], signal)).toEqual([]);
    expect(request).not.toHaveBeenCalled();
  });

  it("kart yoksa null döner (Review verisi gizlenmez)", async () => {
    request.mockResolvedValue({ items: [], unavailable: [gameId] });
    expect(await gameLookup.card(gameId, signal)).toBeNull();
  });

  it("arama metni yoksa popülerlik sıralı katalog, varsa yerel önek araması kullanır", async () => {
    request.mockResolvedValue({ items: [], page: 0, size: 12, totalElements: 0 });
    await gameLookup.search("", 0, 12, signal);
    expect(lastCall().path).toBe("/api/v1/game/games?page=0&size=12&sort=POPULARITY&direction=DESC");
    await gameLookup.search("  half  ", 2, 12, signal);
    expect(lastCall().path).toBe("/api/v1/game/games/search?page=2&size=12&q=half");
    expect(lastCall().init).toMatchObject({ credentials: "omit" });
  });
});

describe("loadReviewResource", () => {
  it("her kaynak türünü tek uca bağlar", async () => {
    request.mockResolvedValue({});
    await loadReviewResource({ kind: "statistics", gameId }, signal);
    expect(lastCall().path).toBe(`/api/v1/review/games/${gameId}/statistics`);
    await loadReviewResource({ kind: "featured", gameId, query: { page: 0, size: 20, minLikes: 0 } }, signal);
    expect(lastCall().path).toContain("/reviews/featured?");
    await loadReviewResource({ kind: "ownReview", gameId }, signal);
    expect(lastCall().path).toBe(`/api/v1/review/games/${gameId}/reviews/mine`);
    await loadReviewResource({ kind: "myReviews", page: 1, size: 20 }, signal);
    expect(lastCall().path).toBe("/api/v1/review/reviews/mine?page=1&size=20");
    await loadReviewResource({ kind: "gameCards", gameIds: [gameId] }, signal);
    expect(lastCall().path).toBe("/api/v1/game/games/batch");
    await loadReviewResource({ kind: "gameSearch", q: "a", page: 0, size: 12 }, signal);
    expect(lastCall().path).toContain("/api/v1/game/games/search?");
  });
});

describe("yazma uçları: yöntem, yol, CSRF ve gövde", () => {
  it("puan yazar; PUT + CSRF + {rating}", async () => {
    request.mockResolvedValue({ gameId, rating: 8 });
    await reviewMutations.rate(gameId, 8, signal);
    const { path, init } = lastCall();
    expect(path).toBe(`/api/v1/review/games/${gameId}/rating`);
    expect(init).toMatchObject({ method: "PUT", cache: "no-store" });
    expect(init.headers).toMatchObject({ "X-XSRF-TOKEN": "review-test-csrf", "Content-Type": "application/json" });
    expect(init.body).toBe(JSON.stringify({ rating: 8 }));
  });

  it("geçersiz puanı ağa göndermez", () => {
    for (const rating of [0, 11, 7.5, Number.NaN]) {
      expect(() => reviewMutations.rate(gameId, rating, signal)).toThrow(ApiHttpError);
    }
    expect(request).not.toHaveBeenCalled();
  });

  it("tavsiyeyi true ve false olarak ayrı gönderir; boolean dışını reddeder", async () => {
    request.mockResolvedValue({});
    await reviewMutations.recommend(gameId, false, signal);
    expect(lastCall().init.body).toBe(JSON.stringify({ recommended: false }));
    await reviewMutations.recommend(gameId, true, signal);
    expect(lastCall().init.body).toBe(JSON.stringify({ recommended: true }));
    expect(() => reviewMutations.recommend(gameId, "true" as unknown as boolean, signal)).toThrow(ApiHttpError);
  });

  it("incelemeyi yalnız {body} ile ve temizlenmiş metinle oluşturur", async () => {
    request.mockResolvedValue(review);
    await reviewMutations.createReview(gameId, "  Güzel oyun \n", signal);
    const { path, init } = lastCall();
    expect(path).toBe(`/api/v1/review/games/${gameId}/reviews`);
    expect(init.method).toBe("POST");
    expect(init.body).toBe(JSON.stringify({ body: "Güzel oyun" }));
    expect(Object.keys(JSON.parse(init.body as string))).toEqual(["body"]);
  });

  it("geçersiz metni ağa göndermez", () => {
    expect(() => reviewMutations.createReview(gameId, "   ", signal)).toThrow(ApiHttpError);
    expect(() => reviewMutations.createReview(gameId, "a".repeat(5001), signal)).toThrow(ApiHttpError);
    expect(() => reviewMutations.updateReview(reviewId, 0, "", signal)).toThrow(ApiHttpError);
    expect(request).not.toHaveBeenCalled();
  });

  it("düzenlemede gerçek version'ı ve yalnız metni gönderir", async () => {
    request.mockResolvedValue(review);
    await reviewMutations.updateReview(reviewId, 3, "Yeni metin", signal);
    const { path, init } = lastCall();
    expect(path).toBe(`/api/v1/review/reviews/${reviewId}`);
    expect(init.method).toBe("PATCH");
    expect(JSON.parse(init.body as string)).toEqual({ version: 3, body: "Yeni metin" });
  });

  it("geçersiz version ile düzenleme ve silme yapılmaz", () => {
    expect(() => reviewMutations.updateReview(reviewId, -1, "x", signal)).toThrow(ApiHttpError);
    expect(() => reviewMutations.updateReview(reviewId, 1.5, "x", signal)).toThrow(ApiHttpError);
    expect(() => reviewMutations.deleteReview(reviewId, -1, signal)).toThrow(ApiHttpError);
    expect(request).not.toHaveBeenCalled();
  });

  it("silmeyi version sorgusu ve gövdesiz DELETE ile yapar", async () => {
    request.mockResolvedValue(undefined);
    await reviewMutations.deleteReview(reviewId, 3, signal);
    const { path, init } = lastCall();
    expect(path).toBe(`/api/v1/review/reviews/${reviewId}?version=3`);
    expect(init.method).toBe("DELETE");
    expect(init.body).toBeUndefined();
    expect(init.headers).not.toHaveProperty("Content-Type");
  });

  it("beğenme ve beğeniyi kaldırma gövdesizdir", async () => {
    request.mockResolvedValue({ reviewId, likeCount: 1, likedByActor: true });
    await reviewMutations.like(reviewId, signal);
    expect(lastCall()).toMatchObject({ path: `/api/v1/review/reviews/${reviewId}/like`, init: { method: "PUT" } });
    expect(lastCall().init.body).toBeUndefined();
    await reviewMutations.unlike(reviewId, signal);
    expect(lastCall()).toMatchObject({ init: { method: "DELETE" } });
  });
});

describe("hata ve CSRF davranışı", () => {
  it("409 sürüm çakışmasını olduğu gibi iletir ve OTOMATİK tekrarlamaz", async () => {
    request.mockRejectedValue(new ApiHttpError(409));
    await expect(reviewMutations.updateReview(reviewId, 1, "x", signal)).rejects.toMatchObject({ status: 409 });
    expect(request).toHaveBeenCalledTimes(1);
  });

  it("429 Retry-After bilgisini korur ve tekrar denemez", async () => {
    request.mockRejectedValue(new ApiHttpError(429, { retryAfterSeconds: 30 }));
    await expect(reviewMutations.createReview(gameId, "metin", signal)).rejects.toMatchObject({ status: 429, retryAfterSeconds: 30 });
    expect(request).toHaveBeenCalledTimes(1);
  });

  it("CSRF reddinde belirteci yeniler ve isteği yalnız BİR kez tekrarlar", async () => {
    request
      .mockRejectedValueOnce(new ApiHttpError(403, { code: "CSRF_TOKEN_INVALID" }))
      .mockResolvedValueOnce(undefined)
      .mockResolvedValueOnce({ reviewId, likeCount: 2, likedByActor: true });
    expect(await reviewMutations.like(reviewId, signal)).toMatchObject({ likeCount: 2 });
    expect(request.mock.calls.map(([path]) => path)).toEqual([`/api/v1/review/reviews/${reviewId}/like`, "/api/v1/auth/csrf", `/api/v1/review/reviews/${reviewId}/like`]);
  });

  it("CSRF ikinci kez reddedilirse hata verir", async () => {
    request.mockImplementation(async (path) => {
      if (path === "/api/v1/auth/csrf") return undefined;
      throw new ApiHttpError(403, { code: "CSRF_TOKEN_INVALID" });
    });
    await expect(reviewMutations.like(reviewId, signal)).rejects.toMatchObject({ status: 403, code: "CSRF_TOKEN_INVALID" });
    // 1 ilk deneme + 1 csrf yenileme + 1 tekrar; üçüncü deneme yoktur.
    expect(request).toHaveBeenCalledTimes(3);
  });

  it("CSRF çerezi yoksa önce belirteci alır", async () => {
    document.cookie = "XSRF-TOKEN=; max-age=0; path=/";
    request.mockImplementation(async (path) => {
      if (path === "/api/v1/auth/csrf") document.cookie = "XSRF-TOKEN=primed; path=/";
      return undefined;
    });
    await reviewMutations.unlike(reviewId, signal);
    expect(request.mock.calls[0][0]).toBe("/api/v1/auth/csrf");
    expect((request.mock.calls[1][1] as { headers: Record<string, string> }).headers["X-XSRF-TOKEN"]).toBe("primed");
  });

  it("yanıt gövdesindeki sunucu metnini hata nesnesine taşımaz", async () => {
    request.mockRejectedValue(new ApiHttpError(500));
    const error = await reviewMutations.like(reviewId, signal).catch((failure: unknown) => failure);
    expect((error as ApiHttpError).message).not.toMatch(/sql|stack/i);
  });
});
