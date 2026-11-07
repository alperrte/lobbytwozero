"use client";
import { useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { useHardwareMutation, useHardwareQuery } from "../hooks/use-hardware-query";
import { hardwareApi } from "../services/hardware-api";
import type { Review } from "../types/hardware";
import { button, primaryButton, panel, HardwareDialog, HardwareEmpty, HardwareError, HardwareLoading, HardwarePagination } from "./hardware-ui";
import { ReportReviewForm, ReviewForm } from "./review-form";

export function ProductReviews({ productId }: { productId: string }) {
  const t = useTranslations("Hardware");
  const format = useFormatter();
  const [page, setPage] = useState(0);
  const [mineOpen, setMineOpen] = useState(false);
  const [minePage, setMinePage] = useState(0);
  const [editor, setEditor] = useState<Review | "new">();
  const [deleting, setDeleting] = useState<Review>();
  const [reporting, setReporting] = useState<string>();
  const reviews = useHardwareQuery(`reviews:${productId}:${page}`, (signal) => hardwareApi.reviews(productId, page, signal));
  const mine = useHardwareQuery(`mine:${minePage}`, (signal) => hardwareApi.myReviews(minePage, signal), mineOpen);
  function refresh() { reviews.reload(); mine.reload(); }
  const mutation = useHardwareMutation(refresh);
  function reviewCard(review: Review) {
    return <article key={review.id} className={`${panel} p-5`}>
      <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-text-secondary">
        <span>
          {review.ownedByActor ? t("reviews.yours") : t("reviews.member")} · {format.dateTime(new Date(review.createdAt), { dateStyle: "medium" })}
        </span>
        <span>{review.rating === null ? t("reviews.noRating") : t("reviews.ratingValue", { rating: review.rating })}</span>
      </div>
      <p className="mt-4 whitespace-pre-wrap break-words text-sm leading-7">{review.body}</p>
      <div className="mt-4 flex flex-wrap gap-2">{review.ownedByActor ? <>
        {review.status === "VISIBLE" ? <button className={button} onClick={() => setEditor(review)}>{t("common.edit")}</button> : <span className="text-sm text-warning">{t("reviews.hidden")}</span>}
        <button className={button} onClick={() => setDeleting(review)}>{t("common.delete")}</button>
      </> : <button className={button} onClick={() => setReporting(review.id)}>{t("reviews.report")}</button>}</div>
    </article>;
  }
  return <section id="reviews" className="mt-8 space-y-5 border-t border-border pt-7">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 className="font-display text-2xl font-semibold">{t("reviews.title")}</h2>
        {reviews.data ? <p className="mt-1 text-sm text-text-secondary">{reviews.data.summary.averageRating === null ? t("reviews.unrated") : t("reviews.summary", { average: format.number(reviews.data.summary.averageRating, { maximumFractionDigits: 2 }), count: reviews.data.summary.ratedReviews })}</p> : null}
      </div>
      {reviews.data ? <div className="flex flex-wrap gap-2">
        <button className={button} onClick={() => setMineOpen(!mineOpen)} aria-expanded={mineOpen}>{t("reviews.mine")}</button>
        <button className={primaryButton} onClick={() => setEditor("new")}>{t("reviews.write")}</button>
      </div> : null}
    </div>
    {reviews.loading ? <HardwareLoading /> : null}
    <HardwareError error={reviews.error} retry={reviews.reload} />
    {reviews.data?.items.length === 0 ? <HardwareEmpty title={t("reviews.emptyTitle")} description={t("reviews.emptyDescription")} /> : null}
    <div className="space-y-4">{reviews.data?.items.map(reviewCard)}</div>
    {reviews.data ? <HardwarePagination page={page} totalPages={reviews.data.totalPages} onPage={setPage} /> : null}
    {mineOpen ? <section className={`${panel} space-y-4 p-5`}>
      <h3 className="font-display text-lg font-semibold">{t("reviews.mine")}</h3>
      <p className="text-sm text-text-secondary">{t("reviews.mineHint")}</p>
      {mine.loading ? <HardwareLoading /> : null}
      <HardwareError error={mine.error} retry={mine.reload} />
      {mine.data?.items.filter((review) => review.productId === productId).map(reviewCard)}
      {mine.data && !mine.data.items.some((review) => review.productId === productId) ? <p className="text-sm text-text-secondary">{t("reviews.mineEmpty")}</p> : null}
      {mine.data ? <HardwarePagination page={minePage} totalPages={mine.data.totalPages} onPage={setMinePage} /> : null}
    </section> : null}
    {editor ? <ReviewForm productId={productId} review={editor === "new" ? undefined : editor} onClose={() => setEditor(undefined)} onSaved={refresh} /> : null}
    {reporting ? <ReportReviewForm reviewId={reporting} onClose={() => setReporting(undefined)} /> : null}
    {deleting ? <HardwareDialog title={t("reviews.deleteTitle")} onClose={() => setDeleting(undefined)} busy={mutation.pending}>
      <p className="text-sm text-text-secondary">{t("reviews.deleteDescription")}</p>
      <HardwareError error={mutation.error} />
      <button className={primaryButton} disabled={mutation.pending || mutation.requiresReview} onClick={async () => { await mutation.run(async () => { await hardwareApi.deleteReview(deleting); setDeleting(undefined); }); }}>{t("common.confirmDelete")}</button>
    </HardwareDialog> : null}
  </section>;
}
