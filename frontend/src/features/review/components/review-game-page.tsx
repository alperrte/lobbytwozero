"use client";

import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { useReviewResource } from "../hooks/use-review-resource";
import type { GameReviewStatistics, ReviewResponse } from "../types/review";
import { FeaturedReviews } from "./featured-reviews";
import { GameHeader } from "./game-header";
import { RatingPanel } from "./rating-panel";
import { RecommendationPanel } from "./recommendation-panel";
import { ReviewBoundary } from "./review-boundary";
import { ReviewAlert, Skeleton, actionClass, focusRing } from "./review-ui";
import { StatisticsOverview } from "./statistics-view";

/**
 * Oyun bazlı çalışma ekranı: üstte özet sayılar, ortada kullanıcının kendi puanı ve tavsiyesi (iki BAĞIMSIZ kayıt),
 * altta öne çıkan incelemeler. Puan veya tavsiye kaydedilince özet sayılar sunucudan yeniden okunur.
 */
export function ReviewGamePage({ gameId }: { gameId: string }) {
  return (
    <ReviewBoundary>
      <GameWorkspace gameId={gameId} />
    </ReviewBoundary>
  );
}

function GameWorkspace({ gameId }: { gameId: string }) {
  const t = useTranslations("Review");
  const stats = useReviewResource<GameReviewStatistics>({ kind: "statistics", gameId });
  // Kullanıcının bu oyunda görünür incelemesi varsa "yaz" yerine "incelemeni aç" sunulur (yoksa/okunamazsa yazma bağlantısı).
  const mine = useReviewResource<ReviewResponse | null>({ kind: "ownReview", gameId });
  return (
    <>
      <nav aria-label={t("breadcrumb")} className="text-sm">
        <Link href="/reviews" className={`rounded-control text-text-secondary underline-offset-2 hover:text-text-primary hover:underline ${focusRing}`}>{t("hub.title")}</Link>
      </nav>

      <GameHeader gameId={gameId}>
        {mine.data ? (
          <Link href={{ pathname: "/reviews/[reviewId]", params: { reviewId: mine.data.id } }} className={actionClass("primary")}>{t("game.openMine")}</Link>
        ) : (
          <Link href={{ pathname: "/reviews/games/[gameId]/write", params: { gameId } }} className={actionClass("primary")}>{t("game.write")}</Link>
        )}
        <Link href={{ pathname: "/reviews/games/[gameId]/statistics", params: { gameId } }} className={actionClass("secondary")}>{t("game.statistics")}</Link>
      </GameHeader>

      <section aria-label={t("game.overview")} className="space-y-3">
        {stats.data ? <StatisticsOverview stats={stats.data} /> : null}
        {stats.loading && !stats.data ? (
          <div role="status" aria-label={t("loading")} className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <span className="sr-only">{t("loading")}</span>
            {[0, 1, 2, 3].map((key) => <Skeleton key={key} className="h-28" />)}
          </div>
        ) : null}
        <ReviewAlert error={stats.error} onRetry={stats.reload} />
      </section>

      <section aria-labelledby="review-mine-title" className="space-y-3">
        <div className="space-y-1">
          <h2 id="review-mine-title" className="font-display text-xl font-semibold">{t("game.mineTitle")}</h2>
          <p className="max-w-prose text-sm leading-6 text-text-secondary">{t("game.mineDescription")}</p>
        </div>
        <div className="grid gap-4 lg:grid-cols-2">
          <RatingPanel gameId={gameId} onChanged={stats.reload} />
          <RecommendationPanel gameId={gameId} onChanged={stats.reload} />
        </div>
      </section>

      <FeaturedReviews gameId={gameId} />
    </>
  );
}
