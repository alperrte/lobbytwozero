"use client";

import { useId } from "react";
import { useLocale, useTranslations } from "next-intl";
import { formatAverage, formatCount, formatPercentage } from "../services/format";
import type { GameReviewStatistics } from "../types/review";
import { Panel, Stat } from "./review-ui";

/**
 * Özet sayılar. Dört ölçü BİLEREK ayrıdır ve birbirine karıştırılmaz: puan veren kullanıcı sayısı, aktif METİN inceleme
 * sayısı, tavsiye sayıları. Veri yoksa `null` ortalama "0/10", `null` yüzde "%0" olarak çizilmez.
 */
export function StatisticsOverview({ stats }: { stats: GameReviewStatistics }) {
  const t = useTranslations("Review");
  const locale = useLocale();
  const average = formatAverage(stats.ratings.averageRating, locale);
  const percentage = formatPercentage(stats.recommendations.recommendedPercentage, locale);
  const { recommendedCount, notRecommendedCount, totalCount } = stats.recommendations;
  return (
    <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <Stat
        label={t("stats.average")}
        value={average ? t("stats.averageValue", { average }) : t("stats.noRating")}
        hint={average ? t("stats.averageHint") : t("stats.noRatingHint")}
      />
      <Stat label={t("stats.ratingCount")} value={formatCount(stats.ratings.ratingCount, locale)} hint={t("stats.ratingCountHint")} />
      <Stat label={t("stats.activeReviews")} value={formatCount(stats.reviews.activeCount, locale)} hint={t("stats.activeReviewsHint")} />
      <Stat
        label={t("stats.recommended")}
        value={percentage ?? t("stats.noRecommendation")}
        hint={totalCount > 0 ? t("stats.recommendedHint", { yes: formatCount(recommendedCount, locale), no: formatCount(notRecommendedCount, locale) }) : t("stats.noRecommendationHint")}
      />
    </dl>
  );
}

/** 1–10 puan histogramı: 10 kovanın tümü gösterilir (boş kova 0 ile). Çubuklar dekoratiftir; sayılar metindir. */
export function RatingHistogram({ stats }: { stats: GameReviewStatistics }) {
  const t = useTranslations("Review");
  const locale = useLocale();
  const titleId = useId();
  const { distribution, ratingCount, averageRating } = stats.ratings;
  const max = Math.max(1, ...distribution.map((bucket) => bucket.userCount));
  const rows = [...distribution].sort((left, right) => right.rating - left.rating);
  const average = formatAverage(averageRating, locale);
  return (
    <Panel title={t("stats.histogramTitle")} titleId={titleId} description={ratingCount > 0 ? t("stats.histogramSummary", { count: formatCount(ratingCount, locale), average: average ?? "" }) : t("stats.histogramEmpty")}>
      <ol aria-labelledby={titleId} className="space-y-2">
        {rows.map((bucket) => (
          <li key={bucket.rating} className="grid grid-cols-[2rem_minmax(0,1fr)_3.5rem] items-center gap-3 text-sm">
            <span className="sr-only">{t("stats.bucket", { rating: bucket.rating, count: formatCount(bucket.userCount, locale) })}</span>
            <span aria-hidden="true" className="text-end font-display font-semibold tabular-nums">{bucket.rating}</span>
            <span aria-hidden="true" className="h-3 overflow-hidden rounded-full bg-surface-elevated">
              <span className="block h-full rounded-full bg-primary" style={{ width: `${(bucket.userCount / max) * 100}%` }} />
            </span>
            <span aria-hidden="true" className="text-end tabular-nums text-text-secondary">{formatCount(bucket.userCount, locale)}</span>
          </li>
        ))}
      </ol>
    </Panel>
  );
}

/** Olumlu/olumsuz tavsiye dağılımı. Yüzdeler sunucunun 0–100 değeridir; toplam 0 ise çubuk çizilmez. */
export function RecommendationBreakdown({ stats }: { stats: GameReviewStatistics }) {
  const t = useTranslations("Review");
  const locale = useLocale();
  const titleId = useId();
  const { recommendedCount, notRecommendedCount, totalCount, recommendedPercentage, notRecommendedPercentage } = stats.recommendations;
  const yes = formatPercentage(recommendedPercentage, locale);
  const no = formatPercentage(notRecommendedPercentage, locale);
  return (
    <Panel title={t("stats.recommendationTitle")} titleId={titleId} description={t("stats.recommendationDescription")}>
      {totalCount === 0 || recommendedPercentage === null || notRecommendedPercentage === null ? (
        <p className="text-sm text-text-secondary">{t("stats.recommendationEmpty")}</p>
      ) : (
        <div className="space-y-4">
          <div aria-hidden="true" className="flex h-4 overflow-hidden rounded-full bg-surface-elevated">
            <span className="block h-full bg-success" style={{ width: `${recommendedPercentage}%` }} />
            <span className="block h-full bg-primary" style={{ width: `${notRecommendedPercentage}%` }} />
          </div>
          <dl className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1">
              <dt className="flex items-center gap-2 text-sm font-semibold"><span aria-hidden="true" className="size-3 rounded-full bg-success" />{t("recommendation.yes")}</dt>
              <dd className="font-display text-xl font-semibold tabular-nums">{yes}</dd>
              <dd className="text-xs text-text-secondary">{t("stats.users", { count: formatCount(recommendedCount, locale) })}</dd>
            </div>
            <div className="space-y-1">
              <dt className="flex items-center gap-2 text-sm font-semibold"><span aria-hidden="true" className="size-3 rounded-full bg-primary" />{t("recommendation.no")}</dt>
              <dd className="font-display text-xl font-semibold tabular-nums">{no}</dd>
              <dd className="text-xs text-text-secondary">{t("stats.users", { count: formatCount(notRecommendedCount, locale) })}</dd>
            </div>
          </dl>
          <p className="text-xs leading-5 text-text-secondary">{t("stats.recommendationBasis", { total: formatCount(totalCount, locale) })}</p>
        </div>
      )}
    </Panel>
  );
}
