import { pages } from "@/shared/config/pages";
import type { SearchProvider } from "./search";

/** Var olan sayfaları arar. Başlık/açıklama `Pages.<id>.*` mesajlarından gelir. */
export const pagesProvider: SearchProvider = {
  id: "pages",
  getEntries: (translate) =>
    pages.map((page) => ({
      id: `page-${page.id}`,
      group: "pages" as const,
      title: translate(`${page.id}.title`),
      description: translate(`${page.id}.description`),
      href: page.href,
      keywords: page.keywords
    }))
};

/** Yeni kaynaklar (oyun, oyuncu, donanım…) servisleri hazır olunca bu listeye eklenir. */
export const searchProviders: readonly SearchProvider[] = [pagesProvider];
