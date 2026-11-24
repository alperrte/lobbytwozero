"use client";

import { useEffect, useRef, useState } from "react";
import type { EnrichmentState, GameMetadataView } from "../services/adapters";
import { enrichmentApi } from "../services/enrichment-api";
import { recommendationCache } from "../services/recommendation-cache";
import { isAbortError } from "../services/recommendation-errors";
import type { CatalogTerm, GameCard, ProfileCard } from "../types/recommendation";
import { useRecommendationScope } from "./recommendation-scope";

type Loaded<T> = { key: string; data?: T; failed: boolean };

/**
 * Zenginleştirme (ad/kapak/avatar) yardımcı veridir: hatası öneriyi düşürmez, `failed: true` ile kısmi görünüm verir.
 * Bu nedenle rıza kapısını tetiklemez ve hata kartı göstermez; sonuç hesap kapsamlı önbelleğe yazılır.
 */
function useEnrichment<T>(key: string | null, loader: (signal: AbortSignal) => Promise<T>): { data?: T; failed: boolean; loading: boolean } {
  const { accountId } = useRecommendationScope();
  const [state, setState] = useState<Loaded<T>>(() => {
    const cached = key ? recommendationCache.get<T>(accountId, key) : undefined;
    return cached === undefined ? { key: "", failed: false } : { key: key as string, data: cached, failed: false };
  });
  const loaderRef = useRef(loader);
  useEffect(() => {
    loaderRef.current = loader;
  });
  useEffect(() => {
    if (key === null) return;
    const cached = recommendationCache.get<T>(accountId, key);
    const controller = new AbortController();
    let active = true;
    (cached !== undefined ? Promise.resolve(cached) : loaderRef.current(controller.signal)).then(
      (data) => {
        if (!active) return;
        if (cached === undefined) recommendationCache.set(accountId, key, data);
        setState({ key, data, failed: false });
      },
      (error: unknown) => {
        if (active && !isAbortError(error)) setState({ key, failed: true });
      }
    );
    return () => {
      active = false;
      controller.abort();
    };
  }, [key, accountId]);
  if (key === null) return { failed: false, loading: false };
  return state.key === key ? { data: state.data, failed: state.failed, loading: false } : { failed: false, loading: true };
}

const idsKey = (ids: readonly string[]) => [...ids].map((id) => id.toLowerCase()).sort().join(",");

export function useGameCards(ids: readonly string[]): EnrichmentState<GameCard> & { loading: boolean } {
  const key = ids.length ? `enrich:games:${idsKey(ids)}` : null;
  const result = useEnrichment(key, (signal) => enrichmentApi.gameCards(ids, signal));
  return { data: key === null ? new Map() : result.data, failed: result.failed, loading: result.loading };
}

export function useGameMetadata(ids: readonly string[]): { data?: Map<string, GameMetadataView>; failed: boolean } {
  const key = ids.length ? `enrich:meta:${idsKey(ids)}` : null;
  const result = useEnrichment(key, (signal) => enrichmentApi.gameMetadata(ids, signal));
  return { data: result.data, failed: result.failed };
}

export function useProfileCards(ids: readonly string[]): { data?: Map<string, ProfileCard>; failed: boolean; loading: boolean } {
  const key = ids.length ? `enrich:profiles:${idsKey(ids)}` : null;
  const result = useEnrichment(key, (signal) => enrichmentApi.profileCards(ids, signal));
  return { data: key === null ? new Map() : result.data, failed: result.failed, loading: result.loading };
}

/** Game Service tür/platform terimleri: filtre seçenekleri gerçek katalog kodlarından gelir (uydurma liste yok). */
export function useCatalogTerms(kind: "GENRE" | "PLATFORM"): { data?: CatalogTerm[]; failed: boolean; loading: boolean } {
  return useEnrichment(`enrich:terms:${kind}`, (signal) => enrichmentApi.gameTerms(kind, signal));
}
