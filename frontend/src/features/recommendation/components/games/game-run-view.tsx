"use client";

import { useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { useCatalogTerms, useGameCards, useGameMetadata } from "../../hooks/use-enrichment";
import { useItemFeedback } from "../../hooks/use-item-feedback";
import { useLabels } from "../../hooks/use-labels";
import { toGameViews } from "../../services/adapters";
import type { RecommendationRun } from "../../types/recommendation";
import { LimitationsNotice, RecommendationEmpty } from "../recommendation-ui";
import { FeedbackActions } from "./feedback-actions";
import { GameRecommendationCard } from "./game-recommendation-card";

const PAGE = 12;

/** Çalışmanın üst bilgisi: oluşturulma zamanı ve kullanılan filtreler. */
export function RunMeta({ run }: { run: RecommendationRun }) {
  const t = useTranslations("Recommendation.games");
  const format = useFormatter();
  const genres = useCatalogTerms("GENRE");
  const platforms = useCatalogTerms("PLATFORM");
  const labels = useLabels();
  const filters = [
    run.genre ? labels.term(genres.data, run.genre) : null,
    run.platform ? labels.term(platforms.data, run.platform) : null
  ].filter((entry): entry is string => entry !== null);
  return <p className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-text-secondary">
    <span>{t("createdAt", { date: format.dateTime(new Date(run.createdAt), { dateStyle: "medium", timeStyle: "short" }) })}</span>
    <span>{filters.length ? t("filtersUsed", { filters: filters.join(", ") }) : t("noFilters")}</span>
  </p>;
}

/**
 * Bir öneri çalışmasının oyun listesi. Kartlar istemci tarafında 12'şer açılır; geri bildirim yalnız görünen öğeler için okunur
 * (toplu okuma ucu yok; okuma kotası korunur). `fresh`: az önce üretildi, geri bildirim yok (okuma atılmaz).
 */
export function GameRunView({ run, fresh = false }: { run: RecommendationRun; fresh?: boolean }) {
  const t = useTranslations("Recommendation.games");
  const feedbackText = useTranslations("Recommendation.feedback");
  const [visible, setVisible] = useState(PAGE);
  const shown = run.items.slice(0, visible);
  const ids = shown.map((item) => item.gameId);
  const cards = useGameCards(ids);
  const metadata = useGameMetadata(ids);
  const genres = useCatalogTerms("GENRE");
  const labels = useLabels();
  const feedback = useItemFeedback(run.runId, shown.map((item) => item.itemId), fresh);
  const views = toGameViews(shown, cards, metadata.data);

  if (run.status === "EMPTY" || run.items.length === 0) {
    return <div className="space-y-4">
      <RecommendationEmpty icon="gamepad" title={t("emptyRunTitle")} description={t("emptyRunDescription")} />
      <LimitationsNotice codes={run.limitations} />
    </div>;
  }

  return <div className="space-y-5">
    <LimitationsNotice codes={run.limitations} />
    {cards.failed ? <p className="text-sm text-text-secondary">{t("detailsUnavailable")}</p> : null}
    <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-label={t("listLabel")}>
      {views.map((view) => {
        const title = view.card?.title ?? t("unknownTitle");
        const problem = feedback.problem?.itemId === view.item.itemId ? feedback.problem : undefined;
        return <li key={view.item.itemId} className="min-w-0">
          <GameRecommendationCard view={view} genreLabel={(code) => labels.term(genres.data, code)} footer={<div className="space-y-2">
            <FeedbackActions gameTitle={title} entry={feedback.entryOf(view.item.itemId)} pending={!!feedback.pending[view.item.itemId]} onSelect={(value) => void feedback.submit(view.item.itemId, value)} />
            {problem ? <p role="alert" className="text-xs leading-5 text-brand-text">{feedbackText(problem.kind === "conflict" ? "conflict" : "failed")}</p> : null}
          </div>} />
        </li>;
      })}
    </ul>
    {visible < run.items.length
      ? <div className="flex justify-center"><button type="button" className={buttonClass("secondary", "md")} onClick={() => setVisible((count) => count + PAGE)}>{t("showMore", { count: Math.min(PAGE, run.items.length - visible) })}</button></div>
      : null}
  </div>;
}
