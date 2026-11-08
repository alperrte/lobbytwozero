import { marketForCurrency } from "./price-markets";
const filterKeys = ["q", "genre", "platform", "language", "earlyAccess", "releasedAfter", "releasedBefore", "popularOnly", "minRating", "discountedOnly"] as const;

/** Boş filtreler gönderilmez: q= ve boş tür/dil backend'de geçerli bir filtre değildir. */
export function discoveryQuery(input: URLSearchParams): URLSearchParams {
  const result = new URLSearchParams({ page: input.get("page") || "0", size: "20" });
  for (const key of filterKeys) {
    const value = input.get(key)?.trim();
    if (value) result.set(key, value);
  }
  result.set("sort", ["TITLE", "RELEASE_DATE", "POPULARITY", "RATING"].includes(input.get("sort") ?? "") ? input.get("sort")! : "TITLE");
  result.set("direction", input.get("direction") === "DESC" ? "DESC" : "ASC");
  // Ülke ve para birimi birlikte seçilir (USD→US, EUR→DE, GBP→GB); desteklenmeyen değer (ör. eski TRY) varsayılana düşer.
  const market = marketForCurrency(input.get("currency"));
  result.set("country", market.country);
  result.set("currency", market.currency);
  return result;
}

export function safeExternalUrl(value: string | null | undefined): string | undefined {
  if (!value) return undefined;
  try {
    const url = new URL(value);
    return url.protocol === "https:" && !url.username && !url.password ? url.href : undefined;
  } catch { return undefined; }
}

export const filterChipKeys = ["genre", "platform", "language", "earlyAccess", "releasedAfter", "releasedBefore", "popularOnly", "minRating", "discountedOnly"] as const;
export type FilterChipKey = (typeof filterChipKeys)[number];
export type FilterError = "dateRange" | "minRating";

/** Aktif filtreleri sabit sırayla döndürür; boş değerler ve bilinmeyen anahtarlar yok sayılır. */
export function activeFilters(input: URLSearchParams): { key: FilterChipKey; value: string }[] {
  return filterChipKeys.flatMap((key) => {
    const value = input.get(key)?.trim();
    return value ? [{ key, value }] : [];
  });
}

/** Backend sözleşmesiyle aynı kurallar: puan 1–10 ve en çok iki ondalık, tarih aralığı ters olamaz. */
export function validateFilters(input: URLSearchParams): FilterError[] {
  const errors: FilterError[] = [];
  const rating = input.get("minRating")?.trim();
  if (rating && !(/^\d{1,2}(\.\d{1,2})?$/.test(rating) && Number(rating) >= 1 && Number(rating) <= 10)) errors.push("minRating");
  const after = input.get("releasedAfter")?.trim();
  const before = input.get("releasedBefore")?.trim();
  if (after && before && after > before) errors.push("dateRange");
  return errors;
}

/** Geçersiz filtreyi sunucuya göndermeden çıkarır; kullanıcıya hata ayrıca gösterilir. */
export function sanitizedDiscoveryQuery(input: URLSearchParams): { query: URLSearchParams; errors: FilterError[] } {
  const errors = validateFilters(input);
  const clean = new URLSearchParams(input);
  if (errors.includes("minRating")) clean.delete("minRating");
  if (errors.includes("dateRange")) { clean.delete("releasedAfter"); clean.delete("releasedBefore"); }
  return { query: discoveryQuery(clean), errors };
}

export const catalogueSize = 20;
export function lastPageIndex(totalElements: number, size = catalogueSize): number {
  return Math.max(0, Math.min(10000, Math.ceil(totalElements / size) - 1));
}
