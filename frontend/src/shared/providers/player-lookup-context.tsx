"use client";

import { createContext } from "react";

/** Navbar'daki `@` aramasının tek sonuç satırı. */
export interface PlayerLookupHit {
  userId: string;
  name: string;
  username: string | null;
}

/**
 * `shared` katmanı `features`'ı import etmez; oyuncu aramasını User özelliği bu bağlam üzerinden verir.
 * Sağlayıcı yoksa (`null`) navbar `@` modunu yalnız "tüm sonuçlar" bağlantısıyla gösterir.
 */
export interface PlayerLookup {
  search(term: string, signal: AbortSignal): Promise<PlayerLookupHit[]>;
  /** Arama metni kabul edilebilir mi (alt/üst uzunluk). Kabul edilirse temizlenmiş metin, edilmezse `null`. */
  normalize(term: string): string | null;
}

export const PlayerLookupContext = createContext<PlayerLookup | null>(null);
