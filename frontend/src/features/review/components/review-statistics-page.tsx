"use client";

import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { useReviewResource } from "../hooks/use-review-resource";
import type { GameReviewStatistics } from "../types/review";
import { GameHeader } from "./game-header";
import { ReviewBoundary } from "./review-boundary";
import { Panel, ReviewAlert, Skeleton, actionClass, focusRing } from "./review-ui";
import { RatingHistogram, RecommendationBreakdown, StatisticsOverview } from "./statistics-view";

/** Oyun istatistikleri (REV-14): tüm değerler gerçek Review istatistik API'sinden gelir; veri yoksa sahte değer çizilmez. */
export function ReviewStatisticsPage({ gameId }: { gameId: string }) {
  return (
    <ReviewBoundary>
      <StatisticsWorkspace gameId={gameId} />
    </ReviewBoundary>
  );
}

function StatisticsWorkspace({ gameId }: { gameId: string }) {
  const t = useTranslations("Review");
  const stats = useReviewResource<GameReviewStatistics>({ kind: "statistics", gameId });
  const empty = stats.data && stats.data.ratings.ratingCount === 0 && stats.data.reviews.activeCount === 0 && stats.data.recommendations.totalCount === 0;
  return (
    <>
      <nav aria-label={t("breadcrumb")} className="flex flex-wrap gap-x-2 text-sm text-text-secondary">
        <Link href="/reviews" className={`rounded-control underline-offset-2 hover:text-text-primary hover:underline ${focusRing}`}>{t("hub.title")}</Link>
        <span aria-hidden="true">/</span>
        <Link href={{ pathname: "/reviews/games/[gameId]", params: { gameId } }} className={`rounded-control underline-offset-2 hover:text-text-primary hover:underline ${focusRing}`}>{t("game.reviewsLink")}</Link>
      </nav>

      <GameHeader gameId={gameId} heading="p">
        <Link href={{ pathname: "/reviews/games/[gameId]", params: { gameId } }} className={actionClass("secondary")}>{t("statistics.back")}</Link>
      </GameHeader>

      <header className="space-y-2">
        <h1 className="font-display text-3xl font-semibold text-balance">{t("statistics.title")}</h1>
        <p className="max-w-prose text-sm leading-6 text-text-secondary">{t("statistics.description")}</p>
      </header>

      {stats.loading && !stats.data ? (
        <div role="status" aria-label={t("loading")} className="space-y-3">
          <span className="sr-only">{t("loading")}</span>
          <Skeleton className="h-28" />
          <Skeleton className="h-64" />
        </div>
      ) : null}
      <ReviewAlert error={stats.error} onRetry={stats.reload} />

      {stats.data ? (
        <div className="space-y-6" aria-busy={stats.loading}>
          {empty ? <Panel><p className="text-sm text-text-secondary">{t("statistics.empty")}</p></Panel> : null}
          <StatisticsOverview stats={stats.data} />
          <div className="grid gap-4 lg:grid-cols-2">
            <RatingHistogram stats={stats.data} />
            <RecommendationBreakdown stats={stats.data} />
          </div>
          <Panel>
            <p className="text-xs leading-5 text-text-secondary">{t("statistics.note")}</p>
          </Panel>
        </div>
      ) : null}
    </>
  );
}
