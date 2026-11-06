import type { AppPathname } from "@/shared/lib/i18n/routing";
import { pages } from "./pages";

export type PublicRoute = {
  /** Dahili yol (`routing.ts` anahtarı); her dil için görünen adres `getPathname` ile üretilir. */
  path: AppPathname;
  changeFrequency: "monthly";
  priority: number;
};

/**
 * Gerçekten yayınlanmış, herkese açık VE indekslenebilir rotalar. sitemap.xml yalnız bunları listeler
 * (her biri her dil için ayrı adres olarak). Kaynak `pages.ts`'tir; `indexable: false` sayfalar
 * (iletişim, yasal, ayarlar) ve (auth), (dashboard), (admin) gibi özel alanlar buraya girmez.
 */
export const publicRoutes: readonly PublicRoute[] = pages
  .filter((page) => page.indexable)
  .map((page) => ({
    path: page.href,
    changeFrequency: "monthly" as const,
    priority: page.href === "/" ? 1 : 0.6
  }));
