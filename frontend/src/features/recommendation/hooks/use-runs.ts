"use client";

import { useState } from "react";
import { recommendationApi } from "../services/recommendation-api";
import { recommendationCache } from "../services/recommendation-cache";
import { isAbortError, isConsentRequired } from "../services/recommendation-errors";
import type { GenerateGamesRequest, RecommendationRun, RunSummary } from "../types/recommendation";
import { useRecommendationScope } from "./recommendation-scope";
import { useRecommendationMutation } from "./use-recommendation-mutation";
import { useRecommendationQuery } from "./use-recommendation-query";

export const LATEST_RUN_KEY = "runs:latest";
const HISTORY_PAGE_SIZE = 10;

/**
 * Sayfa açılışında YENİ ÖNERİ ÜRETİLMEZ (generation kotası ve geçmiş kaydı boşa harcanmaz): yalnız son kayıtlı çalışma
 * okunur (`GET /runs?size=1` → `GET /runs/{id}`). Hiç geçmiş yoksa `null` döner ve arayüz "öneri oluştur" çağrısı gösterir.
 */
export function useLatestRun() {
  return useRecommendationQuery<RecommendationRun | null>(LATEST_RUN_KEY, async (signal) => {
    const page = await recommendationApi.listRuns({ size: 1 }, signal);
    const first = page.items[0];
    return first ? recommendationApi.getRun(first.runId, signal) : null;
  }, { cache: true });
}

/** `POST /games`: yalnız kullanıcı eylemiyle. Başarılı yanıt son çalışma önbelleğine yazılır, geçmiş listesi tazelenir. */
export function useGenerateRun() {
  const { accountId } = useRecommendationScope();
  const mutation = useRecommendationMutation();
  const [run, setRun] = useState<RecommendationRun>();
  async function generate(request: GenerateGamesRequest) {
    const result = await mutation.run(() => recommendationApi.generateGames(request));
    if (result?.ok) {
      recommendationCache.invalidate(accountId, "runs:");
      recommendationCache.set(accountId, LATEST_RUN_KEY, result.value);
      setRun(result.value);
    }
    return result;
  }
  return { ...mutation, run, generate };
}

export function useRunDetail(runId: string) {
  return useRecommendationQuery(`runs:detail:${runId}`, (signal) => recommendationApi.getRun(runId, signal), { cache: true });
}

/** İmleçli geçmiş (`beforeRunId`). İlk sayfa önbelleğe alınır; "Daha fazla" ek sayfaları sona ekler. */
export function useRunHistory() {
  const { consentRequired } = useRecommendationScope();
  const first = useRecommendationQuery("runs:history", (signal) => recommendationApi.listRuns({ size: HISTORY_PAGE_SIZE }, signal), { cache: true });
  const [extra, setExtra] = useState<{ base: unknown; items: RunSummary[]; nextCursor: string | null }>();
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState<unknown>();
  // İlk sayfa değişirse (yenileme) eklenmiş sayfalar geçersizdir.
  const appended = extra && extra.base === first.data ? extra : undefined;
  const items = [...(first.data?.items ?? []), ...(appended?.items ?? [])];
  const nextCursor = appended ? appended.nextCursor : first.data?.nextCursor ?? null;

  async function loadMore() {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    setMoreError(undefined);
    try {
      const page = await recommendationApi.listRuns({ size: HISTORY_PAGE_SIZE, beforeRunId: nextCursor });
      setExtra({ base: first.data, items: [...(appended?.items ?? []), ...page.items], nextCursor: page.nextCursor });
    } catch (error) {
      if (isAbortError(error)) return;
      if (isConsentRequired(error)) consentRequired();
      setMoreError(error);
    } finally {
      setLoadingMore(false);
    }
  }

  return { ...first, items, nextCursor, loadMore, loadingMore, moreError };
}
