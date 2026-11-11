"use client";

import { useId, useState, type FormEvent } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { useReviewResource } from "../hooks/use-review-resource";
import { FEATURED_SIZES, featuredQueryFrom, featuredSearchParams } from "../schemas/review-schema";
import { formatCount } from "../services/format";
import type { FeaturedQuery, FeaturedReviewsResponse } from "../types/review";
import { ReportReview } from "./report-review";
import { ReviewCard } from "./review-card";
import { EmptyState, Pagination, Panel, ReviewAlert, Skeleton, actionClass, focusRing } from "./review-ui";

/**
 * Öne çıkan incelemeler (REV-09). Sunucu sırası: beğeni sayısı azalan, oluşturulma azalan. "En yeni" gibi sıralama
 * seçenekleri backend'de olmadığından SUNULMAZ. Sayfa, boyut ve asgari beğeni filtresi URL sorgusundadır (paylaşılabilir,
 * geri/ileri çalışır). Toplam sayı sunucunun filtrelenmiş kümesinin gerçek toplamıdır.
 */
export function FeaturedReviews({ gameId }: { gameId: string }) {
  const t = useTranslations("Review");
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const query = featuredQueryFrom(new URLSearchParams(params.toString()));
  const result = useReviewResource<FeaturedReviewsResponse>({ kind: "featured", gameId, query });
  const filterId = useId();
  const [draftMin, setDraftMin] = useState<string | null>(null);
  const minValue = draftMin ?? String(query.minLikes);

  function navigate(next: FeaturedQuery) {
    const search = featuredSearchParams(next).toString();
    // Yalnız bu bileşenin sahip olduğu sorgu anahtarları değişir; görünen sayfa yolu (dil önekli) aynı kalır.
    router.push(search ? `${pathname}?${search}` : pathname, { scroll: false });
  }

  function applyFilter(event: FormEvent) {
    event.preventDefault();
    const parsed = Number(minValue);
    const minLikes = Number.isInteger(parsed) && parsed >= 0 ? Math.min(parsed, 2147483647) : 0;
    setDraftMin(null);
    navigate({ ...query, minLikes, page: 0 });
  }

  const data = result.data;
  const titleId = useId();
  return (
    <Panel
      title={t("featured.title")}
      titleId={titleId}
      description={t("featured.description")}
    >
      <div className="space-y-5">
        <form onSubmit={applyFilter} className="flex flex-wrap items-end gap-3" aria-label={t("featured.filterLabel")}>
          <div className="space-y-1">
            <label htmlFor={`${filterId}-min`} className="block text-sm font-semibold">{t("featured.minLikes")}</label>
            <input
              id={`${filterId}-min`}
              type="number"
              inputMode="numeric"
              min={0}
              step={1}
              value={minValue}
              onChange={(event) => setDraftMin(event.target.value)}
              className={`block min-h-11 w-32 rounded-control border border-control-border bg-surface-elevated px-3 text-sm text-text-primary ${focusRing}`}
            />
          </div>
          <div className="space-y-1">
            <label htmlFor={`${filterId}-size`} className="block text-sm font-semibold">{t("featured.pageSize")}</label>
            <select
              id={`${filterId}-size`}
              value={query.size}
              onChange={(event) => navigate({ ...query, size: Number(event.target.value), page: 0 })}
              className={`block min-h-11 rounded-control border border-control-border bg-surface-elevated px-3 text-sm text-text-primary ${focusRing}`}
            >
              {FEATURED_SIZES.map((size) => <option key={size} value={size}>{size}</option>)}
            </select>
          </div>
          <button type="submit" className={actionClass("secondary")}>{t("featured.apply")}</button>
          {query.minLikes > 0 ? <button type="button" className={actionClass("ghost")} onClick={() => { setDraftMin(null); navigate({ ...query, minLikes: 0, page: 0 }); }}>{t("featured.clear")}</button> : null}
        </form>

        {result.loading && !data ? (
          <div role="status" aria-label={t("loading")} className="space-y-3">
            <span className="sr-only">{t("loading")}</span>
            <Skeleton className="h-36" />
            <Skeleton className="h-36" />
          </div>
        ) : null}
        <ReviewAlert error={result.error} onRetry={result.reload} />

        {data ? (
          <div className="space-y-4" aria-busy={result.loading}>
            <p role="status" className="text-sm text-text-secondary">{t("featured.total", { count: formatCount(data.totalElements, locale) })}</p>
            {data.items.length === 0 ? (
              data.totalElements === 0 ? (
                <EmptyState
                  title={query.minLikes > 0 ? t("featured.emptyFiltered") : t("featured.empty")}
                  hint={query.minLikes > 0 ? t("featured.emptyFilteredHint") : t("featured.emptyHint")}
                />
              ) : (
                <EmptyState
                  title={t("featured.pageEmpty")}
                  action={<button type="button" className={actionClass("secondary")} onClick={() => navigate({ ...query, page: 0 })}>{t("featured.firstPage")}</button>}
                />
              )
            ) : (
              <ul className="space-y-3">
                {data.items.map((item) => (
                  <li key={item.review.id}>
                    <ReviewCard review={item.review} likeCount={item.likeCount} likedByActor={item.likedByActor} actions={<ReportReview reviewId={item.review.id} ownedByActor={item.review.ownedByActor} />} />
                  </li>
                ))}
              </ul>
            )}
            <Pagination page={data.page} size={data.size} totalElements={data.totalElements} label={t("featured.pagination")} onChange={(page) => navigate({ ...query, page })} />
          </div>
        ) : null}
      </div>
    </Panel>
  );
}
