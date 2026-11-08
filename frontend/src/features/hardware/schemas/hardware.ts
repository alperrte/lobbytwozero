import { categories, type Category } from "../types/hardware";
import { technicalQueryKeys } from "./spec-profile";

export const numericSpecs = { capacityGb: 1048576, clockMhz: 20000, coreCount: 1024, powerWatts: 10000 } as const;
export const isCategory = (value: string | null): value is Category => categories.includes(value as Category);
export const isUuid = (value: string) => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
export const safeTextPattern = "[^\\x00-\\x1F\\x7F<>]+";
export function safeExternalUrl(value: string): string | undefined {
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.username || url.password) return;
    return url.href;
  } catch { return; }
}
export function readCatalogQuery(search: URLSearchParams): string {
  const query = new URLSearchParams();
  for (const key of ["q", "brand", "brandId", "category", ...technicalQueryKeys]) {
    const value = search.get(key);
    // Onay kutusu filtresi yalnız "true" olabilir; başka değer API'ye gitmez.
    if (value && (key !== "integratedGraphics" || value === "true")) query.set(key, value);
  }
  const page = Number(search.get("page"));
  query.set("page", String(Number.isInteger(page) && page >= 0 && page <= 10000 ? page : 0));
  query.set("size", "12");
  return `?${query}`;
}
