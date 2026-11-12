"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { useReviewResource } from "../hooks/use-review-resource";
import { hubQueryFrom } from "../schemas/review-schema";
import { formatCount, formatDateTime, safeCoverUrl, wasEdited } from "../services/format";
import type { ReviewGameCard, ReviewPage, ReviewResponse } from "../types/review";
import { ReviewBoundary } from "./review-boundary";
import { RetryButton } from "./retry-button";
import { EmptyState, Pagination, ReviewAlert, ReviewCover, Skeleton, actionClass, focusRing } from "./review-ui";

const PAGE_SIZE = 20;

/**
 * İncelemelerim: kullanıcının TÜM oyunlardaki görünür incelemeleri (Review `reviews/mine`, yeniden eskiye, sayfalı). Oyun adı
 * ve kapağı Game Service'ten sayfa başına TEK toplu istekle alınır; Game ulaşılamazsa inceleme listesi yine görünür.
 */
export function MyReviewsPage() {
  return (
    <ReviewBoundary>
      <MyReviewsWorkspace />
    </ReviewBoundary>
  );
}

function MyReviewsWorkspace() {
  const t = useTranslations("Review");
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const { page } = hubQueryFrom(new URLSearchParams(params.toString()));
  const reviews = useReviewResource<ReviewPage>({ kind: "myReviews", page, size: PAGE_SIZE });
  const ids = reviews.data?.items.map((item) => item.gameId) ?? [];
  const cards = useReviewResource<ReviewGameCard[]>(ids.length > 0 ? { kind: "gameCards", gameIds: ids } : null);
  const byGame = new Map<string, ReviewGameCard>((cards.data ?? []).map((card) => [card.gameId.toLowerCase(), card]));

  function go(next: number) {
    const search = new URLSearchParams();
    if (next > 0) search.set("page", String(next));
    const text = search.toString();
    router.push(text ? `${pathname}?${text}` : pathname, { scroll: false });
  }

  const data = reviews.data;
  return (
    <>
      <nav aria-label={t("breadcrumb")} className="text-sm">
        <Link href="/reviews" className={`rounded-control text-text-secondary underline-offset-2 hover:text-text-primary hover:underline ${focusRing}`}>{t("hub.title")}</Link>
      </nav>
      <header className="space-y-2">
        <h1 className="font-display text-3xl font-semibold text-balance sm:text-4xl">{t("mine.title")}</h1>
        <p className="max-w-prose text-sm leading-6 text-text-secondary">{t("mine.description")}</p>
      </header>

      {reviews.loading && !data ? (
        <div role="status" aria-label={t("loading")} className="space-y-3">
          <span className="sr-only">{t("loading")}</span>
          <Skeleton className="h-36" />
          <Skeleton className="h-36" />
        </div>
      ) : null}
      <ReviewAlert error={reviews.error} onRetry={reviews.reload} />

      {data ? (
        <section aria-label={t("mine.title")} className="space-y-4" aria-busy={reviews.loading}>
          <p role="status" className="text-sm text-text-secondary">{t("mine.total", { count: formatCount(data.totalElements, locale) })}</p>
          {data.items.length === 0 ? (
            data.totalElements === 0 ? (
              <EmptyState title={t("mine.empty")} hint={t("mine.emptyHint")} action={<Link href="/reviews" className={actionClass("primary")}>{t("mine.find")}</Link>} />
            ) : (
              <EmptyState title={t("featured.pageEmpty")} action={<button type="button" className={actionClass("secondary")} onClick={() => go(0)}>{t("featured.firstPage")}</button>} />
            )
          ) : (
            <ul className="space-y-3">
              {data.items.map((review) => (
                <li key={review.id}>
                  <MyReviewItem review={review} card={byGame.get(review.gameId.toLowerCase())} cardsLoading={cards.loading} />
                </li>
              ))}
            </ul>
          )}
          <Pagination page={data.page} size={data.size} totalElements={data.totalElements} label={t("mine.pagination")} onChange={go} />
        </section>
      ) : null}
      {cards.error ? <p className="text-sm text-text-secondary">{t("mine.gamesUnavailable")} <RetryButton error={cards.error} onRetry={cards.reload} /></p> : null}
    </>
  );
}

function MyReviewItem({ review, card, cardsLoading }: { review: ReviewResponse; card?: ReviewGameCard; cardsLoading: boolean }) {
  const t = useTranslations("Review");
  const locale = useLocale();
  const title = card?.title ?? t("game.unknown");
  const created = formatDateTime(review.createdAt, locale);
  const updated = wasEdited(review.createdAt, review.updatedAt) ? formatDateTime(review.updatedAt, locale) : null;
  return (
    <article className="flex min-w-0 flex-wrap gap-4 rounded-panel border border-border bg-surface p-4 sm:flex-nowrap">
      {cardsLoading && !card ? <Skeleton className="aspect-video w-32 shrink-0" /> : <ReviewCover url={card ? safeCoverUrl(card) : null} title={title} className="w-32 shrink-0 rounded-control" />}
      <div className="min-w-0 flex-1 space-y-3">
        <div className="space-y-1">
          <h2 className="font-display text-lg font-semibold [overflow-wrap:anywhere]">{title}</h2>
          {review.status !== "VISIBLE" ? <p className="inline-block rounded-control border border-primary/40 bg-primary/10 px-2 py-0.5 text-xs font-semibold text-brand-text">{t(`moderation.${review.status}.badge`)}</p> : null}
          <p className="flex flex-wrap gap-x-3 text-xs text-text-secondary">
            <span>{created ? t("card.created", { date: created }) : t("card.dateUnknown")}</span>
            {updated ? <span>{t("card.edited", { date: updated })}</span> : null}
          </p>
        </div>
        <p className="line-clamp-4 whitespace-pre-wrap text-sm leading-6 [overflow-wrap:anywhere]">{review.body}</p>
        <div className="flex flex-wrap gap-2">
          <Link href={{ pathname: "/reviews/[reviewId]", params: { reviewId: review.id } }} className={actionClass("secondary")}>{t("card.openMine")}</Link>
          <Link href={{ pathname: "/reviews/games/[gameId]", params: { gameId: review.gameId } }} className={actionClass("ghost")}>{t("game.reviewsLink")}</Link>
        </div>
      </div>
    </article>
  );
}
