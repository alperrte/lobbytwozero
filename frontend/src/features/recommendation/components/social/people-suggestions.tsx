"use client";

import { useTranslations } from "next-intl";
import { cn } from "@/shared/utils/cn";
import { useProfileCards } from "../../hooks/use-enrichment";
import { usePlayerSuggestions } from "../../hooks/use-social-recommendations";
import { toPlayerViews } from "../../services/adapters";
import type { PlayerFilters, PlayerRecommendationKind } from "../../types/recommendation";
import { LimitationsNotice, RecommendationEmpty, RecommendationError, RecommendationSkeleton } from "../recommendation-ui";
import { PlayerSuggestionCard } from "./player-suggestion-card";

/** Oyuncu/arkadaş öneri listesi: yükleniyor, hata, boş ve sonuç durumları; profil kartları tek toplu istekle eklenir. */
export function PeopleSuggestions({ kind, filters, compact = false, limit }: { kind: PlayerRecommendationKind; filters: PlayerFilters; compact?: boolean; limit?: number }) {
  const t = useTranslations("Recommendation.social");
  const people = usePlayerSuggestions(kind, filters);
  const items = (people.data?.items ?? []).slice(0, limit);
  const profiles = useProfileCards(items.map((item) => item.userId));
  const views = toPlayerViews(items, profiles.data);
  const copy = kind === "FRIEND_SUGGESTIONS" ? "friends" : "players";

  if (people.loading && !people.data) return <RecommendationSkeleton count={compact ? 2 : 3} variant={compact ? "row" : "card"} />;
  if (people.error) return <RecommendationError error={people.error} retry={people.reload} compact={compact} />;
  if (!people.data) return null;
  if (!items.length) {
    return <div className="space-y-4">
      <RecommendationEmpty icon="users" title={t(`${copy}.emptyTitle`)} description={t(`${copy}.emptyDescription`)} />
      {compact ? null : <LimitationsNotice codes={people.data.limitations} />}
    </div>;
  }
  return <div className="space-y-4">
    {compact ? null : <LimitationsNotice codes={people.data.limitations} />}
    <ul className={cn("grid gap-3", compact ? "grid-cols-1" : "sm:grid-cols-2 xl:grid-cols-3")} aria-label={t(`${copy}.listLabel`)}>
      {views.map((view) => <li key={view.suggestion.userId} className="min-w-0"><PlayerSuggestionCard view={view} compact={compact} /></li>)}
    </ul>
  </div>;
}
