"use client";

import { useLocale, useMessages, useTranslations } from "next-intl";
import { matchCatalogKey, termLabel } from "../services/adapters";
import type { CatalogTerm } from "../types/recommendation";

type CatalogMessages = { Profile?: { catalog?: { genre?: Record<string, string>; platform?: Record<string, string> }; playerType?: Record<string, string> } };

/**
 * Kodlardan kullanıcıya görünen ad. İki farklı kod ailesi vardır:
 * - Oyun kataloğu (Game Service `/game/terms`): oyun önerisi, donanım ve filtrelerdeki tür/platform kodları.
 * - Kullanıcı tercih kataloğu (`Profile.catalog.*`): benzer oyuncu, arkadaş ve etkinlik önerilerindeki ortak ilgi kodları.
 * Ad bulunamazsa kod olduğu gibi gösterilir (uydurma çeviri yok).
 */
export function useLabels() {
  const locale = useLocale();
  const messages = useMessages() as CatalogMessages;
  const catalog = messages.Profile?.catalog;
  const playerTypes = messages.Profile?.playerType;
  const t = useTranslations("Recommendation.platform");

  function preference(kind: "genre" | "platform", code: string): string {
    const entries = catalog?.[kind] ?? {};
    const key = matchCatalogKey(code, Object.keys(entries));
    return key ? entries[key] : code;
  }

  return {
    term: (terms: readonly CatalogTerm[] | undefined, code: string) => termLabel(terms, code, locale),
    genre: (code: string) => preference("genre", code),
    platform: (code: string) => preference("platform", code),
    playerType: (code: string) => playerTypes?.[code.toUpperCase()] ?? code,
    requirementPlatform: (code: string) => (["windows", "mac", "linux"].includes(code) ? t(code as "windows") : code)
  };
}
