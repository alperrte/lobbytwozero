import type { PostFilters } from "../types/community.types";

export const PAGE_SIZE = 20;
const MIN_QUERY = 2;
const MAX_QUERY = 100;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const isUuid = (value: string): boolean => UUID.test(value);

/** Yerel gün başı → UTC anı (`from` dahil). Geçersiz tarih `undefined`. */
export function startOfLocalDay(date: string): string | undefined {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return undefined;
  const value = new Date(`${date}T00:00:00`);
  return Number.isNaN(value.getTime()) ? undefined : value.toISOString();
}

/** Seçilen son günü dahil saymak için ertesi yerel gün başı (`to` hariç). */
export function endOfLocalDayExclusive(date: string): string | undefined {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return undefined;
  const value = new Date(`${date}T00:00:00`);
  if (Number.isNaN(value.getTime())) return undefined;
  value.setDate(value.getDate() + 1);
  return value.toISOString();
}

/** `q` 2-100 karakter olmalıdır; kısa metin sunucuya gönderilmez (400 yerine arama yok sayılır). */
export function normalizedSearch(q: string): string | undefined {
  const value = q.trim();
  return value.length >= MIN_QUERY ? value.slice(0, MAX_QUERY) : undefined;
}

/** `from` ≥ `to` ise sunucu 400 verir; arayüz bunu form hatası olarak gösterir. */
export function isInvalidRange(filters: Pick<PostFilters, "fromDate" | "toDate">): boolean {
  const from = startOfLocalDay(filters.fromDate);
  const to = endOfLocalDayExclusive(filters.toDate);
  return from !== undefined && to !== undefined && from >= to;
}

/** Akış sorgusu: yalnız API'nin desteklediği parametreler. İmleç, aynı filtre ve `sort` ile istenmelidir. */
export function buildPostQuery(
  filters: PostFilters,
  options: { forumId?: string; cursor?: string | null; limit?: number } = {}
): string {
  const query = new URLSearchParams();
  const q = normalizedSearch(filters.q);
  if (q) query.set("q", q);
  if (filters.gameId && isUuid(filters.gameId)) query.set("gameId", filters.gameId);
  if (options.forumId && isUuid(options.forumId)) query.set("forumId", options.forumId);
  if (filters.category) query.set("category", filters.category);
  if (!isInvalidRange(filters)) {
    const from = startOfLocalDay(filters.fromDate);
    const to = endOfLocalDayExclusive(filters.toDate);
    if (from) query.set("from", from);
    if (to) query.set("to", to);
  }
  query.set("sort", filters.sort);
  query.set("limit", String(options.limit ?? PAGE_SIZE));
  if (options.cursor) query.set("cursor", options.cursor);
  return query.toString();
}
