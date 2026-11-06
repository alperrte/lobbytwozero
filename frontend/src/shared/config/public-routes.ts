import { pages } from "./pages";

/**
 * Gerçekten yayınlanmış, herkese açık VE indekslenebilir rotalar. sitemap.xml yalnız bunları listeler.
 * Kaynak `pages.ts`'tir; `indexable: false` sayfalar (iletişim, yasal) ve (auth), (dashboard),
 * (admin) gibi özel alanlar buraya girmez.
 */
export const publicRoutes = pages
  .filter((page) => page.indexable)
  .map((page) => ({
    path: page.href,
    changeFrequency: "monthly" as const,
    priority: page.href === "/" ? 1 : 0.6
  }));
