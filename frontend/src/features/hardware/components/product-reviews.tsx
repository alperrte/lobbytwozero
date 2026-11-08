"use client";
import { useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { StarRating } from "@/shared/components/ui/star-rating";
import { useHardwareMutation, useHardwareQuery } from "../hooks/use-hardware-query";
import { hardwareApi } from "../services/hardware-api";
import type { Review } from "../types/hardware";
import { ovalButton, ovalPrimaryButton, ovalPanel, HardwareEmpty, HardwareError, HardwareLoading, HardwarePagination } from "./hardware-ui";
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
  const mine = useHardwareQuery(`mine:${minePage}`, (signal) => hardwareApi.myReviews(minePage, signal));
  const ownReview = mine.data?.items.find((review) => review.productId === productId && review.status === "VISIBLE") ?? reviews.data?.items.find((review) => review.ownedByActor && review.status === "VISIBLE");
  const average = reviews.data?.summary.averageRating ?? null;
  function refresh() { reviews.reload(); mine.reload(); }
  const mutation = useHardwareMutation(refresh);
  function reviewCard(review: Review) {
    return <article key={review.id} className={`${ovalPanel} p-5`}>
      <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-text-secondary">
        <span>
          {review.ownedByActor ? t("reviews.yours") : t("reviews.member")} · {format.dateTime(new Date(review.createdAt), { dateStyle: "medium" })}
        </span>
        <StarRating size="sm" value={review.rating} label={t("reviews.ratingLabel")} emptyText={t("reviews.noRating")} formatValue={(value) => t("reviews.ratingValue", { rating: value })} showText />
      </div>
      <p className="mt-4 whitespace-pre-wrap break-words text-sm leading-7">{review.body}</p>
      <div className="mt-4 flex flex-wrap gap-2">{review.ownedByActor ? <>
        {review.status === "VISIBLE" ? <button className={ovalButton} onClick={() => setEditor(review)}>{t("common.edit")}</button> : <span className="text-sm text-warning">{t("reviews.hidden")}</span>}
        <button className={ovalButton} onClick={() => setDeleting(review)}>{t("common.delete")}</button>
      </> : <button className={ovalButton} onClick={() => setReporting(review.id)}>{t("reviews.report")}</button>}</div>
    </article>;
  }
  return <section id="reviews" className="mt-8 space-y-5 border-t border-border pt-7">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 className="font-display text-2xl font-semibold">{t("reviews.title")}</h2>
        {reviews.data ? (average === null
          ? <p className="mt-1 text-sm text-text-secondary">{t("reviews.unrated")}</p>
          : <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1">
            <StarRating size="sm" value={average} label={t("reviews.ratingLabel")} emptyText={t("reviews.unrated")} formatValue={(value) => t("reviews.averageAria", { rating: format.number(value, { maximumFractionDigits: 1 }) })} />
            <span className="text-sm font-semibold">{t("reviews.ratingValue", { rating: Number(average.toFixed(1)) })}</span>
            <span className="text-sm text-text-secondary">{t("reviews.ratedBy", { count: reviews.data.summary.ratedReviews })}</span>
          </div>) : null}
      </div>
      {reviews.data ? <div className="flex flex-wrap gap-2">
        <button className={ovalButton} onClick={() => setMineOpen(!mineOpen)} aria-expanded={mineOpen}>{t("reviews.mine")}</button>
        <button className={ovalPrimaryButton} onClick={() => setEditor(ownReview ?? "new")}>{t(ownReview ? "reviews.editMine" : "reviews.write")}</button>
      </div> : null}
    </div>
    {reviews.loading ? <HardwareLoading /> : null}
    <HardwareError error={reviews.error} retry={reviews.reload} />
    {reviews.data?.items.length === 0 ? <HardwareEmpty title={t("reviews.emptyTitle")} description={t("reviews.emptyDescription")} /> : null}
    <div className="space-y-4">{reviews.data?.items.map(reviewCard)}</div>
    {reviews.data ? <HardwarePagination page={page} totalPages={reviews.data.totalPages} onPage={setPage} /> : null}
    {mineOpen ? <section className={`${ovalPanel} space-y-4 p-5`}>
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
    {deleting ? <ConfirmDialog
      tone="danger"
      title={t("reviews.deleteTitle")}
      description={t("reviews.deleteDescription")}
      confirmLabel={t(mutation.pending ? "common.deleting" : "common.confirmDelete")}
      cancelLabel={t("reviews.confirmBack")}
      busy={mutation.pending}
      onConfirm={async () => { await mutation.run(async () => { await hardwareApi.deleteReview(deleting); setDeleting(undefined); }); }}
      onCancel={() => setDeleting(undefined)}
    >
      <HardwareError error={mutation.error} />
    </ConfirmDialog> : null}
  </section>;
}
