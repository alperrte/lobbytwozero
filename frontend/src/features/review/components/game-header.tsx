"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { useReviewResource } from "../hooks/use-review-resource";
import { safeCoverUrl } from "../services/format";
import type { ReviewGameCard } from "../types/review";
import { RetryButton } from "./retry-button";
import { ReviewCover, Skeleton, focusRing } from "./review-ui";

/**
 * Oyun başlığı: ad ve kapak Game Service'ten (yalnız okuma) gelir. Game ulaşılamazsa Review verisi gizlenmez — başlık
 * yerine tarafsız bir ifade ve "tekrar dene" gösterilir; oyun adı uydurulmaz.
 */
export function GameHeader({ gameId, heading, children }: { gameId: string; heading?: "h1" | "p"; children?: ReactNode }) {
  const t = useTranslations("Review");
  const card = useReviewResource<ReviewGameCard | null>({ kind: "gameCard", gameId });
  const Heading = heading ?? "h1";
  const unavailable = card.data === null || (card.error !== undefined && card.data === undefined);
  const title = card.data?.title ?? t("game.unknown");
  return (
    <header className="flex flex-wrap items-start gap-4 sm:gap-6">
      {card.loading && card.data === undefined ? (
        <Skeleton className="aspect-video w-32 shrink-0 sm:w-44" />
      ) : (
        <ReviewCover url={card.data ? safeCoverUrl(card.data) : null} title={title} className="w-32 shrink-0 rounded-control sm:w-44" />
      )}
      <div className="min-w-0 flex-1 space-y-3">
        <div className="space-y-1">
          <Heading className="font-display text-2xl font-semibold text-balance [overflow-wrap:anywhere] sm:text-3xl">{title}</Heading>
          {unavailable ? (
            <p className="text-sm text-text-secondary">
              {t("game.unavailable")}{" "}
              {card.error ? <RetryButton error={card.error} onRetry={card.reload} /> : null}
            </p>
          ) : null}
          {card.data ? (
            <Link href={{ pathname: "/games/[gameId]", params: { gameId } }} className={`inline-flex min-h-6 items-center rounded-control text-sm text-brand-text underline underline-offset-2 ${focusRing}`}>
              {t("game.open")}
            </Link>
          ) : null}
        </div>
        {children ? <div className="flex flex-wrap gap-2">{children}</div> : null}
      </div>
    </header>
  );
}
