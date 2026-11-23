"use client";

import { useTranslations } from "next-intl";
import { cn } from "@/shared/utils/cn";
import { useGameCards } from "../../hooks/use-enrichment";
import { useEventSuggestions } from "../../hooks/use-social-recommendations";
import { distinctIds, toEventViews } from "../../services/adapters";
import { LimitationsNotice, RecommendationEmpty, RecommendationError, RecommendationSkeleton } from "../recommendation-ui";
import { EventSuggestionCard } from "./event-suggestion-card";

/** Etkinlik önerileri listesi. Bağlı oyunların adı/kapağı tek toplu istekle eklenir; alınamazsa etkinlik yine gösterilir. */
export function EventSuggestions({ size, compact = false, limit }: { size: number; compact?: boolean; limit?: number }) {
  const t = useTranslations("Recommendation.events");
  const events = useEventSuggestions(size);
  const items = (events.data?.items ?? []).slice(0, limit);
  const cards = useGameCards(distinctIds(items.map((item) => item.gameId)));
  const views = toEventViews(items, cards.data);

  if (events.loading && !events.data) return <RecommendationSkeleton count={compact ? 2 : 3} variant={compact ? "row" : "card"} />;
  if (events.error) return <RecommendationError error={events.error} retry={events.reload} compact={compact} />;
  if (!events.data) return null;
  if (!items.length) {
    return <div className="space-y-4">
      <RecommendationEmpty icon="calendar" title={t("emptyTitle")} description={t("emptyDescription")} />
      {compact ? null : <LimitationsNotice codes={events.data.limitations} />}
    </div>;
  }
  return <div className="space-y-4">
    {compact ? null : <LimitationsNotice codes={events.data.limitations} />}
    <ul className={cn("grid gap-3", compact ? "sm:grid-cols-2" : "gap-4 sm:grid-cols-2 xl:grid-cols-3")} aria-label={t("listLabel")}>
      {views.map((view) => <li key={view.event.eventId} className="min-w-0"><EventSuggestionCard view={view} compact={compact} /></li>)}
    </ul>
  </div>;
}
