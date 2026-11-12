/**
 * Review Service API tipleri (kaynak: `backend/services/review-service` DTO'ları ve docs-review/REVIEW_SERVICE_API.md).
 * Zaman alanları UTC ISO-8601 metnidir. Yazarın kullanıcı kimliği `authorUserId` olarak döner; kullanıcı adı veya avatar yanıtta YOKTUR; `ownedByActor`
 * bir profil kimliği değildir.
 */

/** Kullanıcının bir oyuna verdiği 1–10 tam sayı puan (`GET/PUT .../games/{gameId}/rating`). */
export type RatingResponse = {
  gameId: string;
  rating: number;
  version: number;
  createdAt: string;
  updatedAt: string;
};

/** Olumlu/olumsuz tavsiye. Puandan ve metinden bağımsızdır; kayıt yokluğu `false` değildir. */
export type RecommendationResponse = {
  gameId: string;
  recommended: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
};

/** Metinsel inceleme. `body` düz metindir (HTML değildir). `version`, düzenleme/silmede iyimser kilit için gönderilir. */
export type ReviewStatus = "VISIBLE" | "HIDDEN" | "ARCHIVED";

export type ReviewResponse = {
  id: string;
  gameId: string;
  body: string;
  /** Yazarın kullanıcı kimliği (UUID). Ad/avatar Review'dan gelmez; User toplu profil kartından çözülür (`userLookup`). */
  authorUserId: string;
  ownedByActor: boolean;
  /** VISIBLE herkese açık; HIDDEN/ARCHIVED yalnız sahibine görünür (moderasyon). Silinmiş inceleme hiç dönmez. */
  status: ReviewStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
};

export type ReviewLikeResponse = {
  reviewId: string;
  likeCount: number;
  likedByActor: boolean;
};

export type FeaturedReviewItem = {
  review: ReviewResponse;
  likeCount: number;
  /** Yalnız istek sahibinin oyu (oy veren kimlikleri dönmez). Kendi incelemesinde false. */
  likedByActor: boolean;
};

/** `GET /api/v1/review/reviews/mine`: kullanıcının tüm oyunlardaki görünür incelemeleri (yeniden eskiye). */
export type ReviewPage = {
  items: ReviewResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type FeaturedReviewsResponse = {
  items: FeaturedReviewItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type RatingSummary = {
  gameId: string;
  /** Puan yoksa `null` (0/10 değildir). */
  averageRating: number | null;
  ratingCount: number;
};

export type RatingSummariesResponse = { items: RatingSummary[] };

export type RatingBucket = { rating: number; userCount: number };

export type GameReviewStatistics = {
  gameId: string;
  ratings: { ratingCount: number; averageRating: number | null; distribution: RatingBucket[] };
  reviews: { activeCount: number };
  recommendations: {
    recommendedCount: number;
    notRecommendedCount: number;
    totalCount: number;
    /** 0–100 ölçeği; veri yoksa `null` (sahte %0 değildir). */
    recommendedPercentage: number | null;
    notRecommendedPercentage: number | null;
  };
};

/** Game Service ortak oyun kartı (yalnız okunur; `POST /api/v1/game/games/batch`). */
export type ReviewGameCard = {
  gameId: string;
  title: string;
  coverUrl: string | null;
  externalReferences?: { provider: string; externalId: string }[];
};

/** `adult`: Steam'in yetişkin içerik işareti; `null`/yok = bilinmiyor (false değildir). */
export type ReviewGameSearchItem = { game: ReviewGameCard; releaseDate: string | null; adult?: boolean | null };
export type ReviewGameSearchPage = {
  items: ReviewGameSearchItem[];
  page: number;
  size: number;
  totalElements: number;
  /** +18 oyunlar bu yanıta katıldı mı. İstek `includeAdult=true` ise ve false dönerse yaş doğrulaması geçerli değildir (eski sunucuda alan yoktur). */
  includeAdultApplied?: boolean;
};

/** Hub oyun araması ve sayfalaması: durum URL sorgusundadır. */
export type FeaturedQuery = { page: number; size: number; minLikes: number };

/**
 * User Service toplu profil kartından gelen yazar kartı (`POST /api/v1/user/profiles/batch`). Yalnız görünür hesaplar döner;
 * olmayan/yasaklı/engelli hesaplar yanıtta YOKTUR (ayrım sızmaz) — kartı olmayan yazar "bir oyuncu" gösterilir, bağlantı verilmez.
 */
export type AuthorCard = {
  userId: string;
  username: string | null;
  displayName: string | null;
  /** Doğrulanmış, Gateway'e göreli User medya yolu; yoksa `null`. */
  avatarPath: string | null;
};

/** Yükleme (okuma) kaynakları. Her tür `loadReviewResource` içinde tek bir uca bağlanır. */
export type ReviewResource =
  | { kind: "ownRating"; gameId: string }
  | { kind: "ownRecommendation"; gameId: string }
  | { kind: "statistics"; gameId: string }
  | { kind: "ratingSummary"; gameId: string }
  | { kind: "ratingSummaries"; gameIds: string[] }
  | { kind: "featured"; gameId: string; query: FeaturedQuery }
  | { kind: "review"; reviewId: string }
  | { kind: "ownReview"; gameId: string }
  | { kind: "ownReport"; reviewId: string }
  | { kind: "myReviews"; page: number; size: number }
  | { kind: "gameCards"; gameIds: string[] }
  | { kind: "like"; reviewId: string }
  | { kind: "gameCard"; gameId: string }
  | { kind: "gameSearch"; q: string; page: number; size: number; includeAdult?: boolean }
  | { kind: "authorCards"; userIds: string[] };

/** Rapor gerekçesi (kapalı liste; backend `ReportReason` ile aynı). Geçici sözlük: Admin'in nihai sözlüğüyle birlikte güncellenir. */
export type ReportReason = "SPAM" | "HARASSMENT" | "HATE_SPEECH" | "PERSONAL_INFO" | "SPOILER" | "OFF_TOPIC" | "OTHER";

/** Raportörün kendi raporu. `status` yalnız Review'ın yerel kaydıdır (RECEIVED): Admin'e iletildiği/karara bağlandığı anlamına gelmez. */
export type ReviewReport = {
  id: string;
  reviewId: string;
  reason: ReportReason;
  detail: string | null;
  /** RECEIVED (kaydedildi) veya CLOSED (moderatör kapattı); kapanış sonucu (işlem/reddedildi) raportöre gösterilmez. */
  status: "RECEIVED" | "CLOSED";
  createdAt: string;
};

/** Hata bağlamı: aynı HTTP durumu işleme göre farklı anlam taşır (ör. 409). */
export type ReviewErrorContext = "read" | "rating" | "recommendation" | "create" | "update" | "delete" | "like" | "report";
