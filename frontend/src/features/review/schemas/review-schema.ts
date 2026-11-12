import type { FeaturedQuery } from "../types/review";

/** Backend sınırları (ReviewService / FeaturedReviewController ile aynı). İstemci doğrulaması yalnız UX içindir. */
export const REVIEW_BODY_MAX = 5000;
export const RATING_MIN = 1;
export const RATING_MAX = 10;
export const FEATURED_DEFAULT_SIZE = 20;
export const FEATURED_SIZES = [10, 20, 50] as const;
export const FEATURED_MAX_PAGE = 10000;
export const BATCH_MAX = 50;
export const REPORT_DETAIL_MAX = 500;
export const REPORT_REASONS = ["SPAM", "HARASSMENT", "HATE_SPEECH", "PERSONAL_INFO", "SPOILER", "OFF_TOPIC", "OTHER"] as const;
export const HUB_PAGE_SIZE = 12;
export const HUB_QUERY_MAX = 128;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Kanonik LTZ kimliği (oyun/inceleme). Steam AppID veya başka biçim kabul edilmez. */
export function isReviewUuid(value: unknown): value is string {
  return typeof value === "string" && UUID.test(value);
}

export function isValidRating(value: unknown): value is number {
  return typeof value === "number" && Number.isInteger(value) && value >= RATING_MIN && value <= RATING_MAX;
}

export type BodyIssue = "required" | "tooLong" | "invalidCharacters";

/**
 * İnceleme metni kontrolü. Backend uzunluğu boşluk temizlenmeden ÖNCE (ham metin, UTF-16 birimi) 5000'e karşı sınar; bu
 * yüzden sayaç ham uzunluğu gösterir. Yalnız boşluktan oluşan metin ve satır sonu/sekme dışındaki denetim karakterleri reddedilir.
 */
export function reviewBodyIssue(raw: string): BodyIssue | null {
  if (raw.length > REVIEW_BODY_MAX) return "tooLong";
  if (raw.trim().length === 0) return "required";
  for (const char of raw) {
    const code = char.codePointAt(0) ?? 0;
    const control = code <= 0x1f || (code >= 0x7f && code <= 0x9f);
    if (control && char !== "\n" && char !== "\r" && char !== "\t") return "invalidCharacters";
  }
  return null;
}

/** Rapor açıklaması (isteğe bağlı): en çok 500 karakter, satır sonu/sekme dışında denetim karakteri yok. */
export function reportDetailIssue(raw: string): "tooLong" | "invalidCharacters" | null {
  if (raw.length > REPORT_DETAIL_MAX) return "tooLong";
  for (const char of raw) {
    const code = char.codePointAt(0) ?? 0;
    const control = code <= 0x1f || (code >= 0x7f && code <= 0x9f);
    if (control && char !== "\n" && char !== "\r" && char !== "\t") return "invalidCharacters";
  }
  return null;
}

export function isReportReason(value: unknown): value is (typeof REPORT_REASONS)[number] {
  return typeof value === "string" && (REPORT_REASONS as readonly string[]).includes(value);
}

/** Gönderilecek metin: baş/son boşluklar temizlenmiş. */
export function reviewBodyPayload(raw: string): string {
  return raw.trim();
}

function boundedInt(value: string | null, fallback: number, min: number, max: number): number {
  if (value === null || !/^\d{1,10}$/.test(value)) return fallback;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) ? Math.min(max, Math.max(min, parsed)) : fallback;
}

/** URL sorgusundan öne çıkanlar sorgusu. Geçersiz/aralık dışı değerler güvenli varsayılana düşer (sunucuya taşınmaz). */
export function featuredQueryFrom(params: URLSearchParams): FeaturedQuery {
  const size = boundedInt(params.get("size"), FEATURED_DEFAULT_SIZE, 1, 50);
  return {
    page: boundedInt(params.get("page"), 0, 0, FEATURED_MAX_PAGE),
    size: (FEATURED_SIZES as readonly number[]).includes(size) ? size : FEATURED_DEFAULT_SIZE,
    minLikes: boundedInt(params.get("minLikes"), 0, 0, 2147483647)
  };
}

/** Varsayılan olmayan değerleri URL'ye yazar; boş sorgu = varsayılan görünüm. */
export function featuredSearchParams(query: FeaturedQuery): URLSearchParams {
  const params = new URLSearchParams();
  if (query.page > 0) params.set("page", String(query.page));
  if (query.size !== FEATURED_DEFAULT_SIZE) params.set("size", String(query.size));
  if (query.minLikes > 0) params.set("minLikes", String(query.minLikes));
  return params;
}

/**
 * Hub sorgusu: arama metni normalize edilir (boşluk sıkıştırma, en çok 128 karakter); sayfa 0–10000. `includeAdult` ("+18 oyunları göster") yalnız tek ve
 * tam olarak `true` değerinde geçerlidir; diğer her değer (`1`, `yes`, boş, tekrarlı) yok sayılır. URL'deki değer hiçbir yetki sağlamaz, kapı sunucudadır.
 */
export function hubQueryFrom(params: URLSearchParams): { q: string; page: number; includeAdult: boolean } {
  const q = (params.get("q") ?? "").replace(/\s+/g, " ").trim().slice(0, HUB_QUERY_MAX);
  const adult = params.getAll("includeAdult");
  return { q, page: boundedInt(params.get("page"), 0, 0, FEATURED_MAX_PAGE), includeAdult: adult.length === 1 && adult[0] === "true" };
}

/** Toplu özet için benzersiz, geçerli ve en çok 50 oyun kimliği (ilk görülme sırası). */
export function batchIds(ids: readonly string[]): string[] {
  return [...new Set(ids.filter(isReviewUuid))].slice(0, BATCH_MAX);
}
