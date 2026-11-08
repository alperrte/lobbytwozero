import { emptyFilters, postCategories, type PostCategory, type PostFilters, type PostView } from "../types/community.types";
import { isUuid } from "./post-query";

/**
 * "Gönderilerim" üç görünümdür; hepsi aynı karma kayıt listesinden (`GET /users/{id}/posts`) türetilir:
 * `posts` = görselli özgün gönderiler, `words` = yalnız metinden oluşan özgün gönderiler (Sözlerim), `reposts` = yeniden paylaşımlar.
 */
export type MyPostsView = "posts" | "words" | "reposts";

/** Bu görünümde API'nin gerçekten desteklediği süzgeçler: kendi özgün gönderilerinde arama/oyun/kategori/tarih vardır. */
export function supportsFilters(view: MyPostsView): boolean {
  return view !== "reposts";
}

/** Yalnız yeni/eski sıralaması sunulur (Popüler bu sayfada yoktur). */
export function normalizeSort(value: string | null | undefined): PostFilters["sort"] {
  return value === "OLDEST" || value === "oldest" ? "OLDEST" : "NEWEST";
}

const DATE = /^\d{4}-\d{2}-\d{2}$/;

/** URL sorgusundan süzgeçler; geçersiz değerler sessizce varsayılana düşer (URL'ye güvenilmez). */
export function filtersFromParams(get: (key: string) => string | null, view: MyPostsView): PostFilters {
  const sort = normalizeSort(get("sort"));
  if (!supportsFilters(view)) return { ...emptyFilters, sort };
  const category = get("category") as PostCategory | null;
  const game = get("game");
  const from = get("from");
  const to = get("to");
  return {
    q: (get("q") ?? "").slice(0, 100),
    gameId: game && isUuid(game) ? game : "",
    category: category && (postCategories as readonly string[]).includes(category) ? category : "",
    fromDate: from && DATE.test(from) ? from : "",
    toDate: to && DATE.test(to) ? to : "",
    sort
  };
}

/** Süzgeçleri URL sorgusuna çevirir; varsayılan değerler yazılmaz. */
export function paramsFromFilters(filters: PostFilters): Record<string, string> {
  const params: Record<string, string> = {};
  if (filters.q.trim()) params.q = filters.q.trim();
  if (filters.gameId) params.game = filters.gameId;
  if (filters.category) params.category = filters.category;
  if (filters.fromDate) params.from = filters.fromDate;
  if (filters.toDate) params.to = filters.toDate;
  if (filters.sort === "OLDEST") params.sort = "oldest";
  return params;
}

/** Etkin süzgeç sayısı ("Filtrele (2)"); sıralama süzgeç sayılmaz. */
export function activeFilterCount(filters: PostFilters): number {
  return [filters.q.trim(), filters.gameId, filters.category, filters.fromDate, filters.toDate].filter(Boolean).length;
}

/**
 * Görünüme ait gönderi mi? Yeniden paylaşımlar (ve ekledikleri açıklama) hiçbir zaman söz sayılmaz; özgün gönderiler görselli
 * ise Gönderilerim'e, görselsiz (yalnız metin) ise Sözlerim'e girer. Kayıtlar kopyalanmaz, aynı liste süzülür.
 */
export function belongsToView(post: PostView, view: MyPostsView): boolean {
  if (view === "reposts") return post.repostOf !== null;
  if (post.repostOf !== null) return false;
  return view === "words" ? post.images.length === 0 : post.images.length > 0;
}
