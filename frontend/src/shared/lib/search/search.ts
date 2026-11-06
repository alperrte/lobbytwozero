import type { AppPathname } from "@/shared/lib/i18n/routing";

/**
 * Global arama çekirdeği (istemci tarafı, saf fonksiyonlar).
 * Şimdilik yalnız var olan sayfalar aranır (bkz. pages-provider.ts). Oyun/oyuncu/donanım
 * aramaları ilgili servisler hazır olunca yeni bir `SearchProvider` olarak eklenir; arayüz değişmez.
 */
export type SearchGroup = "pages";

export type SearchEntry = {
  id: string;
  group: SearchGroup;
  title: string;
  description?: string;
  href: AppPathname;
  keywords?: readonly string[];
};

/** Bir arama kaynağı. `getEntries` çevrilmiş (aktif dile göre) kayıtları döndürür. */
export type SearchProvider = {
  id: string;
  getEntries: (translate: (key: string) => string) => readonly SearchEntry[];
};

/** Küçük harf, aksan/diakritik sızdırmaz (ş→s, ğ→g, ı→i); Türkçe ve İngilizce sorgularda tutarlı. */
export function normalizeQuery(value: string): string {
  return value
    .toLocaleLowerCase("tr")
    .replace(/ı/g, "i")
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .trim();
}

function score(entry: SearchEntry, query: string): number {
  const title = normalizeQuery(entry.title);
  if (title.startsWith(query)) {
    return 3;
  }
  if (title.includes(query)) {
    return 2;
  }
  const haystack = [entry.description ?? "", ...(entry.keywords ?? [])]
    .map(normalizeQuery)
    .join(" ");
  return haystack.includes(query) ? 1 : 0;
}

/** Boş sorguda tüm kayıtlar (sırası korunur); aksi halde skora göre sıralı eşleşenler. */
export function searchEntries(
  entries: readonly SearchEntry[],
  rawQuery: string
): SearchEntry[] {
  const query = normalizeQuery(rawQuery);
  if (!query) {
    return [...entries];
  }

  return entries
    .map((entry, index) => ({ entry, index, value: score(entry, query) }))
    .filter((match) => match.value > 0)
    .sort((a, b) => b.value - a.value || a.index - b.index)
    .map((match) => match.entry);
}

export function collectEntries(
  providers: readonly SearchProvider[],
  translate: (key: string) => string
): SearchEntry[] {
  return providers.flatMap((provider) => provider.getEntries(translate));
}
