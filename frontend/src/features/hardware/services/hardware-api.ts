import { ApiHttpError, apiRequest } from "@/shared/lib/api/client";
import { CSRF_PATH } from "@/shared/lib/session/csrf";
import type { Brand, Campaign, CatalogFacets, FilterOptions, Comparison, ComponentInput, Component, DeviceType, Page, Privacy, Product, Profile, ProfileComparisonItem, ProfileDetail, Review, ReviewPage, Statistics } from "../types/hardware";

const base = "/api/v1/hardware";
const id = encodeURIComponent;
export function queryString(values: Record<string, string | number | undefined>) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) if (value !== undefined && value !== "") query.set(key, String(value));
  return query.size ? `?${query}` : "";
}

// Gateway's existing double-submit contract; never read HttpOnly session cookies.
export function csrfHeader(cookie: string): Record<string, string> {
  for (const name of ["__Host-XSRF-TOKEN", "XSRF-TOKEN"]) {
    const entry = cookie.split(";").map((part) => part.trim()).find((part) => part.startsWith(`${name}=`));
    if (entry) {
      try { return { "X-XSRF-TOKEN": decodeURIComponent(entry.slice(name.length + 1)) }; } catch { return {}; }
    }
  }
  return {};
}

function read<T>(path: string, signal?: AbortSignal) {
  const timeout = AbortSignal.timeout(20000);
  return apiRequest<T>(`${base}${path}`, { signal: signal ? AbortSignal.any([signal, timeout]) : timeout, cache: "no-store" });
}
const currentCsrf = () => csrfHeader(typeof document === "undefined" ? "" : document.cookie);
/** Auth'un `GET /csrf` ucu CSRF cerezini yazar; basarisizlik burada yutulur, yazma istegi kendi hatasini doner. */
async function primeCsrf() {
  try { await apiRequest<void>(CSRF_PATH, { headers: { Accept: "application/json" }, cache: "no-store", signal: AbortSignal.timeout(10000) }); } catch { /* yazma istegi reddedilirse hata orada gorunur */ }
}
/**
 * Yazma istegi: cerez yoksa once CSRF cifti alinir; Gateway `CSRF_TOKEN_INVALID` (403) donerse cerez yenilenir ve istek BIR kez
 * tekrarlanir (Auth'taki akisla ayni). Aksi halde cerezsiz/eski belirteçle giden yorum, puan ve rapor yazmalari 403 alirdi.
 */
async function write<T>(path: string, method: string, body?: unknown) {
  let retried = false;
  for (;;) {
    if (!currentCsrf()["X-XSRF-TOKEN"]) await primeCsrf();
    try {
      return await apiRequest<T>(`${base}${path}`, {
        method, cache: "no-store", signal: AbortSignal.timeout(20000),
        headers: { "Content-Type": "application/json", ...currentCsrf() },
        ...(body === undefined ? {} : { body: JSON.stringify(body) })
      });
    } catch (error) {
      if (!retried && error instanceof ApiHttpError && error.status === 403 && error.code === "CSRF_TOKEN_INVALID") { retried = true; await primeCsrf(); continue; }
      throw error;
    }
  }
}
/** Marka listesi sayfalı gelir; filtre önerileri için tüm sayfalar (en çok 5) birleştirilir. */
async function allBrands(signal?: AbortSignal): Promise<Brand[]> {
  const first = await read<Page<Brand>>("/catalog/brands?page=0&size=100", signal);
  const items = [...first.items];
  for (let page = 1; page < Math.min(first.totalPages, 5); page++) {
    items.push(...(await read<Page<Brand>>(`/catalog/brands?page=${page}&size=100`, signal)).items);
  }
  return items;
}
/**
 * Filtre açılır listelerinin kaynağı. Kategori seçiliyse yalnız o kategoride kayıtlı gerçek değerler gelir;
 * kategori yoksa yalnız üretici listesi sunulur.
 */
async function filterOptions(category: string | undefined, signal?: AbortSignal): Promise<FilterOptions> {
  if (category) return (await read<CatalogFacets>(`/catalog/facets${queryString({ category })}`, signal)).facets;
  return { brandId: (await allBrands(signal)).map((brand) => ({ value: brand.id, label: brand.name, count: 0 })) };
}
export const hardwareApi = {
  allBrands,
  filterOptions,
  facets: (category: string, signal?: AbortSignal) => read<CatalogFacets>(`/catalog/facets${queryString({ category })}`, signal),
  products: (query: string, signal?: AbortSignal) => read<Page<Product>>(`/catalog/products${query}`, signal),
  product: (productId: string, signal?: AbortSignal) => read<Product>(`/catalog/products/${id(productId)}`, signal),
  brands: (page: number, signal?: AbortSignal) => read<Page<Brand>>(`/catalog/brands?page=${page}&size=100`, signal),
  profiles: (page: number, signal?: AbortSignal) => read<Page<Profile>>(`/profiles?page=${page}&size=12`, signal),
  profile: (profileId: string, page: number, signal?: AbortSignal) => read<ProfileDetail>(`/profiles/${id(profileId)}?page=${page}&size=20`, signal),
  statistics: (signal?: AbortSignal) => read<Statistics>("/profiles/statistics", signal),
  createProfile: (body: { name: string; deviceType: DeviceType }) => write<Profile>("/profiles", "POST", body),
  updateProfile: (profileId: string, body: { name: string; deviceType: DeviceType; profileVersion: number }) => write<Profile>(`/profiles/${id(profileId)}`, "PUT", body),
  deleteProfile: (profile: Profile) => write<void>(`/profiles/${id(profile.id)}?profileVersion=${profile.version}`, "DELETE"),
  saveComponent: (profileId: string, body: ComponentInput, componentId?: string) => write<{ profileVersion: number; component: Component }>(`/profiles/${id(profileId)}/components${componentId ? `/${id(componentId)}` : ""}`, componentId ? "PUT" : "POST", body),
  deleteComponent: (profileId: string, componentId: string, version: number) => write<void>(`/profiles/${id(profileId)}/components/${id(componentId)}?profileVersion=${version}`, "DELETE"),
  privacy: (profileId: string, signal?: AbortSignal) => read<Privacy>(`/profiles/${id(profileId)}/privacy`, signal),
  savePrivacy: (profileId: string, body: Omit<Privacy, "profileId">) => write<Privacy>(`/profiles/${id(profileId)}/privacy`, "PUT", body),
  compareProducts: (leftId: string, rightId: string) => write<Comparison>("/comparisons/catalog-products", "POST", { leftId, rightId }),
  compareProfiles: (leftId: string, rightId: string) => write<Comparison<ProfileComparisonItem>>("/comparisons/profiles", "POST", { leftId, rightId }),
  compareComponents: (profileId: string, leftId: string, rightId: string) => write<Comparison>("/comparisons/components", "POST", { left: { profileId, componentId: leftId }, right: { profileId, componentId: rightId } }),
  reviews: (productId: string, page: number, signal?: AbortSignal) => read<ReviewPage>(`/catalog/products/${id(productId)}/reviews?page=${page}&size=10`, signal),
  myReviews: (page: number, signal?: AbortSignal) => read<Page<Review>>(`/reviews/mine?page=${page}&size=20`, signal),
  saveReview: (productId: string, body: { body: string; rating: number | null; reviewVersion?: number }, reviewId?: string) => write<Review>(reviewId ? `/reviews/${id(reviewId)}` : `/catalog/products/${id(productId)}/reviews`, reviewId ? "PUT" : "POST", body),
  deleteReview: (review: Review) => write<void>(`/reviews/${id(review.id)}?reviewVersion=${review.version}`, "DELETE"),
  reportReview: (reviewId: string, body: { reason: string; details?: string }) => write<{ id: string; deliveryStatus: string }>(`/reviews/${id(reviewId)}/reports`, "POST", body),
  campaigns: (page: number, productId?: string, signal?: AbortSignal) => read<Page<Campaign>>(`/campaigns${queryString({ page, size: 12, productId })}`, signal),
  campaign: (campaignId: string, signal?: AbortSignal) => read<Campaign>(`/campaigns/${id(campaignId)}`, signal)
};
