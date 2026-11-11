"use client";

import type { ReactNode } from "react";
import { useLocale, useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Link } from "@/shared/lib/i18n/navigation";
import { formatDateTime, wasEdited } from "../services/format";
import type { ReviewLikeResponse, ReviewResponse } from "../types/review";
import { LikeButton } from "./like-button";
import { focusRing } from "./review-ui";

/**
 * Liste kartı. Metin KULLANICI İÇERİĞİDİR: yalnız düz metin olarak (React kaçışıyla) çizilir; HTML çalıştırılmaz.
 * Yazar adı/avatarı yoktur — backend bunları döndürmez, uydurulmaz. `actions`: ileride raporlama gibi ek eylemler için
 * AÇIK EKLENTİ NOKTASI (REV-07); bugün kullanılmaz ve sahte düğme üretilmez.
 */
export function ReviewCard({ review, likeCount, likedByActor, onLikeChange, actions }: { review: ReviewResponse; likeCount: number; likedByActor?: boolean; onLikeChange?: (like: ReviewLikeResponse) => void; actions?: ReactNode }) {
  const t = useTranslations("Review");
  const locale = useLocale();
  const created = formatDateTime(review.createdAt, locale);
  const updated = wasEdited(review.createdAt, review.updatedAt) ? formatDateTime(review.updatedAt, locale) : null;
  return (
    <article className="min-w-0 space-y-4 rounded-panel border border-border bg-surface p-5">
      <header className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-text-secondary">
        {review.ownedByActor ? <span className="rounded-control border border-primary/40 bg-primary/10 px-2 py-0.5 font-semibold text-brand-text">{t("card.mine")}</span> : null}
        <span>{created ? t("card.created", { date: created }) : t("card.dateUnknown")}</span>
        {updated ? <span>{t("card.edited", { date: updated })}</span> : null}
      </header>
      <p className="line-clamp-6 whitespace-pre-wrap text-sm leading-6 [overflow-wrap:anywhere]">{review.body}</p>
      <footer className="flex flex-wrap items-start justify-between gap-3 border-t border-border pt-4">
        <LikeButton reviewId={review.id} ownedByActor={review.ownedByActor} initialCount={likeCount} initialLiked={likedByActor} onChange={onLikeChange} />
        <div className="flex flex-wrap items-center gap-2">
          {actions}
          <Link href={{ pathname: "/reviews/[reviewId]", params: { reviewId: review.id } }} className={buttonClass("secondary", "md", `min-h-11 ${focusRing}`)}>
            {t(review.ownedByActor ? "card.openMine" : "card.open")}
          </Link>
        </div>
      </footer>
    </article>
  );
}
