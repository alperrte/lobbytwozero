import { ApiHttpError, apiRequest } from "@/shared/lib/api/client";
import { CSRF_HEADER, CSRF_PATH, readCsrfCookie } from "@/shared/lib/session/csrf";
import { batchIds, HUB_QUERY_MAX, isReportReason, isReviewUuid, isValidRating, reportDetailIssue, reviewBodyIssue, reviewBodyPayload } from "../schemas/review-schema";
import type {
  FeaturedQuery,
  FeaturedReviewsResponse,
  ReportReason,
  ReviewPage,
  ReviewReport,
  GameReviewStatistics,
  RatingResponse,
  RatingSummariesResponse,
  RatingSummary,
  RecommendationResponse,
  AuthorCard,
  ReviewGameCard,
  ReviewGameSearchPage,
  ReviewLikeResponse,
  ReviewResource,
  ReviewResponse
} from "../types/review";

/**
 * Review Service istemcisi — TÜM çağrılar Gateway üzerinden (`/api/v1/review/**`) ve ortak `apiRequest` ile yapılır.
 * Tarayıcı Review servisinin portuna gitmez; iç uçlar (`/internal/**`) kullanılmaz. Kimlik HttpOnly oturum çerezidir.
 * Yazma isteklerine CSRF başlığı eklenir; bilinmeyen sonuçlu (ağ/zaman aşımı) yazma isteği otomatik tekrarlanmaz.
 */
const JSON_HEADERS = { Accept: "application/json" } as const;
const readInit = (signal: AbortSignal): RequestInit => ({ signal, cache: "no-store", headers: JSON_HEADERS });

function requireUuid(value: string): string {
  // Geçersiz kimlik ağa çıkmaz; arayüz bunu 400 gibi ele alır.
  if (!isReviewUuid(value)) throw new ApiHttpError(400);
  return value;
}

const reviewPath = (reviewId: string) => `/api/v1/review/reviews/${requireUuid(reviewId)}` as const;
const gamePath = (gameId: string) => `/api/v1/review/games/${requireUuid(gameId)}` as const;

/** Kayıt yokluğu (404) bu uçlarda "henüz verilmedi" demektir; `null` döner. 0 puan veya `false` tavsiye uydurulmaz. */
async function nullIfNotFound<T>(request: Promise<T>): Promise<T | null> {
  try {
    return await request;
  } catch (error) {
    if (error instanceof ApiHttpError && error.status === 404) return null;
    throw error;
  }
}

export function featuredQueryString(query: FeaturedQuery): string {
  const page = Number.isInteger(query.page) && query.page >= 0 && query.page <= 10000 ? query.page : 0;
  const size = Number.isInteger(query.size) && query.size >= 1 && query.size <= 50 ? query.size : 20;
  const minLikes = Number.isInteger(query.minLikes) && query.minLikes >= 0 && query.minLikes <= 2147483647 ? query.minLikes : 0;
  return new URLSearchParams({ page: String(page), size: String(size), minLikes: String(minLikes) }).toString();
}

export const reviewApi = {
  /** `null`: kullanıcı bu oyuna henüz puan vermedi (0 puan değildir). */
  ownRating: (gameId: string, signal: AbortSignal) => nullIfNotFound(apiRequest<RatingResponse>(`${gamePath(gameId)}/rating`, readInit(signal))),
  /** `null`: kullanıcı henüz tavsiye vermedi (`false` değildir). */
  ownRecommendation: (gameId: string, signal: AbortSignal) => nullIfNotFound(apiRequest<RecommendationResponse>(`${gamePath(gameId)}/recommendation`, readInit(signal))),
  statistics: (gameId: string, signal: AbortSignal) => apiRequest<GameReviewStatistics>(`${gamePath(gameId)}/statistics`, readInit(signal)),
  ratingSummary: (gameId: string, signal: AbortSignal) => apiRequest<RatingSummary>(`${gamePath(gameId)}/rating-summary`, readInit(signal)),
  /** Toplu özet (REV-12): en çok 50 benzersiz geçerli kimlik; oyun başına istek yoktur. Boş listede istek atılmaz. */
  async ratingSummaries(gameIds: readonly string[], signal: AbortSignal): Promise<RatingSummariesResponse> {
    const ids = batchIds(gameIds);
    if (ids.length === 0) return { items: [] };
    return apiRequest<RatingSummariesResponse>("/api/v1/review/games/rating-summaries", { ...readInit(signal), method: "POST", headers: { ...JSON_HEADERS, "Content-Type": "application/json", ...(await csrfHeader(signal)) }, body: JSON.stringify({ gameIds: ids }) });
  },
  featured: (gameId: string, query: FeaturedQuery, signal: AbortSignal) => apiRequest<FeaturedReviewsResponse>(`${gamePath(gameId)}/reviews/featured?${featuredQueryString(query)}`, readInit(signal)),
  review: (reviewId: string, signal: AbortSignal) => apiRequest<ReviewResponse>(reviewPath(reviewId), readInit(signal)),
  /** `null`: kullanıcının bu oyunda görünür incelemesi yok (hiç yazmadı ya da sildi). */
  ownReview: (gameId: string, signal: AbortSignal) => nullIfNotFound(apiRequest<ReviewResponse>(`${gamePath(gameId)}/reviews/mine`, readInit(signal))),
  /** `null`: kullanıcı bu incelemeyi raporlamadı. Raportör kimliği ve moderasyon sonucu dönmez. */
  ownReport: (reviewId: string, signal: AbortSignal) => nullIfNotFound(apiRequest<ReviewReport>(`${reviewPath(reviewId)}/reports/mine`, readInit(signal))),
  /** Kullanıcının tüm oyunlardaki görünür incelemeleri; sayfalı, yeniden eskiye. */
  myReviews(page: number, size: number, signal: AbortSignal) {
    const safePage = Number.isInteger(page) && page >= 0 && page <= 10000 ? page : 0;
    const safeSize = Number.isInteger(size) && size >= 1 && size <= 50 ? size : 20;
    return apiRequest<ReviewPage>(`/api/v1/review/reviews/mine?page=${safePage}&size=${safeSize}`, readInit(signal));
  },
  like: (reviewId: string, signal: AbortSignal) => apiRequest<ReviewLikeResponse>(`${reviewPath(reviewId)}/like`, readInit(signal))
};

/** Game Service (yalnız okuma): oyun adı/kapak. Herkese açık uçlar; çerez gönderilmez (aksi halde Gateway CSRF ister). */
export const gameLookup = {
  /** Kart yoksa/ulaşılamıyorsa `null`; Review verisi bu yüzden gizlenmez. */
  async card(gameId: string, signal: AbortSignal): Promise<ReviewGameCard | null> {
    const id = requireUuid(gameId);
    const response = await apiRequest<{ items: ReviewGameCard[]; unavailable?: string[] }>("/api/v1/game/games/batch", {
      signal,
      cache: "no-store",
      credentials: "omit",
      method: "POST",
      headers: { ...JSON_HEADERS, "Content-Type": "application/json" },
      body: JSON.stringify({ gameIds: [id] })
    });
    return response.items.find((item) => item.gameId.toLowerCase() === id.toLowerCase()) ?? null;
  },
  /** Toplu kart (en çok 50 benzersiz kimlik, tek istek); ulaşılamayan oyun listede yoktur. Boş listede istek atılmaz. */
  async cards(gameIds: readonly string[], signal: AbortSignal): Promise<ReviewGameCard[]> {
    const ids = batchIds(gameIds);
    if (ids.length === 0) return [];
    const response = await apiRequest<{ items: ReviewGameCard[]; unavailable?: string[] }>("/api/v1/game/games/batch", {
      signal,
      cache: "no-store",
      credentials: "omit",
      method: "POST",
      headers: { ...JSON_HEADERS, "Content-Type": "application/json" },
      body: JSON.stringify({ gameIds: ids })
    });
    return response.items;
  },
  /** Arama metni yoksa popülerlik sırasıyla katalog; varsa yerel önek araması (`/games/search`). */
  search(q: string, page: number, size: number, signal: AbortSignal): Promise<ReviewGameSearchPage> {
    const query = new URLSearchParams({ page: String(Math.max(0, Math.min(10000, page))), size: String(Math.max(1, Math.min(100, size))) });
    const text = q.trim().slice(0, HUB_QUERY_MAX);
    const path = text ? "/api/v1/game/games/search" : "/api/v1/game/games";
    if (text) query.set("q", text);
    else {
      query.set("sort", "POPULARITY");
      query.set("direction", "DESC");
    }
    return apiRequest<ReviewGameSearchPage>(`${path}?${query}`, { signal, cache: "no-store", credentials: "omit", headers: JSON_HEADERS });
  }
};

/** Yalnız User medya adresi kabul edilir; sunucudan gelse bile başka kaynağa görsel isteği atılmaz. */
const AVATAR_PATH = /^\/api\/v1\/user\/media\/[0-9a-f]{32}(-s)?\.(jpg|png|gif)$/;

type RawAuthorCard = { userId?: unknown; username?: unknown; displayName?: unknown; avatar?: { small?: unknown; large?: unknown } | null };

function avatarPath(raw: RawAuthorCard["avatar"]): string | null {
  const value = typeof raw?.small === "string" && raw.small ? raw.small : raw?.large;
  return typeof value === "string" && AVATAR_PATH.test(value) ? value : null;
}

/**
 * User Service (yalnız okuma): yazar adı/avatarı. Okuma anlamlı POST'tur ama Gateway CSRF ister (`mutate`: reddinde belirteç
 * yenilenip YALNIZ BİR KEZ tekrarlanır). Yazar bilgisi yardımcıdır: User ucu hata verirse inceleme listesi yazarsız çalışır
 * (boş döner); iptal (Abort) ise yukarı iletilir. En çok 50 benzersiz kimlik, tek istek; boş listede istek atılmaz.
 */
export const userLookup = {
  async cards(userIds: readonly string[], signal: AbortSignal): Promise<AuthorCard[]> {
    const ids = batchIds(userIds);
    if (ids.length === 0) return [];
    try {
      const response = await mutate<{ items?: RawAuthorCard[] }>("POST", "/api/v1/user/profiles/batch", signal, { userIds: ids });
      return (response.items ?? []).flatMap((item) =>
        typeof item.userId === "string"
          ? [{ userId: item.userId, username: typeof item.username === "string" ? item.username : null, displayName: typeof item.displayName === "string" ? item.displayName : null, avatarPath: avatarPath(item.avatar) }]
          : []
      );
    } catch (error) {
      if (signal.aborted) throw error;
      return [];
    }
  }
};

export async function loadReviewResource(resource: ReviewResource, signal: AbortSignal): Promise<unknown> {
  switch (resource.kind) {
    case "ownRating": return reviewApi.ownRating(resource.gameId, signal);
    case "ownRecommendation": return reviewApi.ownRecommendation(resource.gameId, signal);
    case "statistics": return reviewApi.statistics(resource.gameId, signal);
    case "ratingSummary": return reviewApi.ratingSummary(resource.gameId, signal);
    case "ratingSummaries": return reviewApi.ratingSummaries(resource.gameIds, signal);
    case "featured": return reviewApi.featured(resource.gameId, resource.query, signal);
    case "review": return reviewApi.review(resource.reviewId, signal);
    case "ownReview": return reviewApi.ownReview(resource.gameId, signal);
    case "ownReport": return reviewApi.ownReport(resource.reviewId, signal);
    case "myReviews": return reviewApi.myReviews(resource.page, resource.size, signal);
    case "gameCards": return gameLookup.cards(resource.gameIds, signal);
    case "like": return reviewApi.like(resource.reviewId, signal);
    case "gameCard": return gameLookup.card(resource.gameId, signal);
    case "gameSearch": return gameLookup.search(resource.q, resource.page, resource.size, signal);
    case "authorCards": return userLookup.cards(resource.userIds, signal);
  }
}

async function primeCsrf(signal: AbortSignal): Promise<string> {
  await apiRequest(CSRF_PATH, readInit(signal));
  const token = readCsrfCookie();
  if (!token) throw new ApiHttpError(403, { code: "CSRF_TOKEN_INVALID" });
  return token;
}

async function csrfHeader(signal: AbortSignal): Promise<Record<string, string>> {
  return { [CSRF_HEADER]: readCsrfCookie() ?? (await primeCsrf(signal)) };
}

/** Durum değiştiren istek. CSRF reddinde (istek işlenmeden) belirteç yenilenir ve YALNIZ BİR KEZ tekrarlanır. */
async function mutate<T>(method: "PUT" | "POST" | "PATCH" | "DELETE", path: `/${string}`, signal: AbortSignal, body?: unknown): Promise<T> {
  let token = readCsrfCookie() ?? (await primeCsrf(signal));
  for (let attempt = 0; ; attempt++) {
    try {
      return await apiRequest<T>(path, {
        signal,
        cache: "no-store",
        method,
        headers: { ...JSON_HEADERS, [CSRF_HEADER]: token, ...(body === undefined ? {} : { "Content-Type": "application/json" }) },
        body: body === undefined ? undefined : JSON.stringify(body)
      });
    } catch (error) {
      if (!(error instanceof ApiHttpError) || error.status !== 403 || error.code !== "CSRF_TOKEN_INVALID" || attempt > 0) throw error;
      token = await primeCsrf(signal);
    }
  }
}

export const reviewMutations = {
  rate(gameId: string, rating: number, signal: AbortSignal): Promise<RatingResponse> {
    if (!isValidRating(rating)) throw new ApiHttpError(400);
    return mutate("PUT", `${gamePath(gameId)}/rating`, signal, { rating });
  },
  recommend(gameId: string, recommended: boolean, signal: AbortSignal): Promise<RecommendationResponse> {
    if (typeof recommended !== "boolean") throw new ApiHttpError(400);
    return mutate("PUT", `${gamePath(gameId)}/recommendation`, signal, { recommended });
  },
  /** 201 + yeni inceleme. Tekrar denemesi yoktur; aynı oyuna ikinci aktif inceleme 409'dur. */
  createReview(gameId: string, body: string, signal: AbortSignal): Promise<ReviewResponse> {
    if (reviewBodyIssue(body) !== null) throw new ApiHttpError(400);
    return mutate("POST", `${gamePath(gameId)}/reviews`, signal, { body: reviewBodyPayload(body) });
  },
  /** Yalnız metni değiştirir (puan/tavsiye değişmez). `version`, kullanıcının gördüğü sürümdür; eskiyse 409. */
  updateReview(reviewId: string, version: number, body: string, signal: AbortSignal): Promise<ReviewResponse> {
    if (!Number.isInteger(version) || version < 0 || reviewBodyIssue(body) !== null) throw new ApiHttpError(400);
    return mutate("PATCH", reviewPath(reviewId), signal, { version, body: reviewBodyPayload(body) });
  },
  /** Yalnız metin incelemesini kaldırır; bağımsız puan ve tavsiye korunur. 204. */
  deleteReview(reviewId: string, version: number, signal: AbortSignal): Promise<void> {
    if (!Number.isInteger(version) || version < 0) throw new ApiHttpError(400);
    return mutate("DELETE", `${reviewPath(reviewId)}?version=${version}`, signal);
  },
  /** İnceleme raporu (201). Mükerrer rapor 409, kendi incelemesi 403, görünmez inceleme 404, kota 429. Otomatik tekrar yok. */
  reportReview(reviewId: string, reason: ReportReason, detail: string, signal: AbortSignal): Promise<ReviewReport> {
    if (!isReportReason(reason) || reportDetailIssue(detail) !== null) throw new ApiHttpError(400);
    const trimmed = detail.trim();
    return mutate("POST", `${reviewPath(reviewId)}/reports`, signal, trimmed ? { reason, detail: trimmed } : { reason });
  },
  like: (reviewId: string, signal: AbortSignal) => mutate<ReviewLikeResponse>("PUT", `${reviewPath(reviewId)}/like`, signal),
  unlike: (reviewId: string, signal: AbortSignal) => mutate<ReviewLikeResponse>("DELETE", `${reviewPath(reviewId)}/like`, signal)
};
