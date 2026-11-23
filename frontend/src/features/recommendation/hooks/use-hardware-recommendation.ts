"use client";

import { useState } from "react";
import { hardwareApi, type HardwareProfile } from "@/features/hardware";
import { recommendationApi } from "../services/recommendation-api";
import type { AssessmentRequest, HardwareAssessment, RequirementPlatform } from "../types/recommendation";
import { useRecommendationMutation } from "./use-recommendation-mutation";
import { useRecommendationQuery } from "./use-recommendation-query";

const MAX_PROFILE_PAGES = 5;

/** Kullanıcının kendi bilgisayar profilleri (Hardware Service; en çok 5 sayfa). Profil yoksa boş dizi. */
export function useHardwareProfiles() {
  return useRecommendationQuery<HardwareProfile[]>("hardware:profiles", async (signal) => {
    const first = await hardwareApi.profiles(0, signal);
    const items = [...first.items];
    for (let page = 1; page < Math.min(first.totalPages, MAX_PROFILE_PAGES); page++) items.push(...(await hardwareApi.profiles(page, signal)).items);
    return items;
  }, { cache: true });
}

export type CompatibleQuery = { profileId: string; platform: RequirementPlatform; genre?: string; size: number };

/**
 * Donanıma uygun oyunlar generation kotasındandır: filtre her değiştiğinde değil, kullanıcı "Önerileri getir" dediğinde
 * çağrılır (`query === null` iken istek yok). Aynı filtre 2 dk önbellekten okunur.
 */
export function useHardwareCompatible(query: CompatibleQuery | null) {
  const key = query ? `hardware:compatible:${query.profileId}:${query.platform}:${query.genre ?? ""}:${query.size}` : null;
  return useRecommendationQuery(key, (signal) => recommendationApi.hardwareCompatible(query as CompatibleQuery, signal), { cache: true });
}

/** Tek oyun donanım değerlendirmesi (`POST /hardware/assessments`). Sonuç yalnız bellekte ve bu pencere açıkken tutulur. */
export function useAssessment() {
  const mutation = useRecommendationMutation();
  const [result, setResult] = useState<{ request: AssessmentRequest; assessment: HardwareAssessment }>();
  async function assess(request: AssessmentRequest) {
    const outcome = await mutation.run(() => recommendationApi.assessHardware(request));
    if (outcome?.ok) setResult({ request, assessment: outcome.value });
    return outcome;
  }
  return { ...mutation, result, assess, reset: () => setResult(undefined) };
}
