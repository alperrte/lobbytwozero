"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import { useReviewAction } from "../hooks/use-review-action";
import { useRetryCountdown } from "../hooks/use-retry-countdown";
import { reviewMutations } from "../services/review-api";
import { retryAfterOf } from "../services/review-errors";
import { formatCount } from "../services/format";
import type { ReviewLikeResponse } from "../types/review";
import { ReviewAlert, focusRing } from "./review-ui";

/**
 * İnceleme beğenisi. Sayı ve "ben beğendim mi" ayrı alanlardır. Beğeni durumu SUNUCU yanıtından gelir (istemci kalıcı sayı
 * tutmaz). Öne çıkanlar ve detay yanıtları `likedByActor` döndürür; durum yine de bilinmiyorsa (`initialLiked === undefined`) düğme
 * tarafsız "Beğen" gösterir ve `aria-pressed` vermez; ilk işlemden sonra (PUT/DELETE idempotenttir) durum bilinir.
 * Kendi incelemeni beğenemezsin: sahip için düğme değil yalnız sayı gösterilir. 429'da `Retry-After` süresince kapanır.
 */
export function LikeButton({ reviewId, ownedByActor, initialCount, initialLiked, onChange }: { reviewId: string; ownedByActor: boolean; initialCount: number; initialLiked?: boolean; onChange?: (like: ReviewLikeResponse) => void }) {
  const t = useTranslations("Review");
  const locale = useLocale();
  const action = useReviewAction();
  const countdown = useRetryCountdown();
  const [latest, setLatest] = useState<ReviewLikeResponse>();
  const count = latest?.likeCount ?? initialCount;
  const liked = latest?.likedByActor ?? initialLiked;

  async function toggle() {
    const result = await action.run((signal) => (liked ? reviewMutations.unlike(reviewId, signal) : reviewMutations.like(reviewId, signal)));
    if (!result) return;
    if (result.ok) {
      setLatest(result.data);
      onChange?.(result.data);
      return;
    }
    const wait = retryAfterOf(result.error);
    if (wait) countdown.start(wait);
  }

  const formatted = formatCount(count, locale);
  if (ownedByActor) {
    return (
      <p className="inline-flex items-center gap-2 text-sm text-text-secondary">
        <Icon name="star" className="size-4" aria-hidden="true" />
        <span>{t("like.count", { count: formatted })}</span>
        <span className="text-xs">· {t("like.own")}</span>
      </p>
    );
  }

  const blocked = countdown.remaining > 0;
  return (
    <div className="space-y-2">
      <button
        type="button"
        onClick={toggle}
        disabled={action.pending || blocked}
        aria-pressed={liked === undefined ? undefined : liked}
        aria-busy={action.pending}
        className={cn(
          "inline-flex min-h-11 items-center gap-2 rounded-control border px-4 text-sm font-semibold transition-colors motion-reduce:transition-none disabled:cursor-not-allowed disabled:opacity-55",
          focusRing,
          liked ? "border-primary bg-primary/10 text-brand-text" : "border-control-border bg-surface-elevated text-text-primary hover:border-primary/60"
        )}
      >
        <Icon name="star" className="size-4" aria-hidden="true" />
        <span>{liked ? t("like.remove") : t("like.add")}</span>
        <span className="tabular-nums text-text-secondary" aria-label={t("like.count", { count: formatted })}>{formatted}</span>
      </button>
      {blocked ? <p role="status" className="text-xs text-text-secondary">{t("like.wait", { seconds: countdown.remaining })}</p> : null}
      {!blocked ? <ReviewAlert error={action.error} context="like" /> : null}
    </div>
  );
}
