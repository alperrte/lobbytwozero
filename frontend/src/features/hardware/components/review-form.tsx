"use client";
import { useState } from "react";
import { useTranslations } from "next-intl";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { StarRating } from "@/shared/components/ui/star-rating";
import { useHardwareMutation } from "../hooks/use-hardware-query";
import { hardwareApi } from "../services/hardware-api";
import { reportReasons, type Review } from "../types/hardware";
import { input, ovalButton as ovalSecondary, ovalPrimaryButton as ovalPrimary, Field, HardwareDialog, HardwareError, HardwareSuccess } from "./hardware-ui";

/**
 * Yarim yildiz (0,5 adim) arayuzde hazirdir, ancak Hardware API'si ve `hardware_reviews.rating` kolonu simdilik yalniz
 * 1-5 tam sayi kabul eder (V5 CHECK + `@Min(1) @Max(5) Integer`). Yarim puan icin servis tarafinda yeni bir migration
 * (numara karari ekipte) ve sozlesme degisikligi gerekir; o zaman bu deger `true` yapilir.
 */
export const REVIEW_HALF_STARS = false;

const ovalInput = `${input} rounded-xl px-4 py-3`;

export function ReviewForm({ productId, review, onClose, onSaved }: { productId: string; review?: Review; onClose: () => void; onSaved: () => void }) {
  const t = useTranslations("Hardware");
  const mutation = useHardwareMutation(onSaved);
  const [rating, setRating] = useState<number | null>(review?.rating ?? null);
  const [draft, setDraft] = useState<string>();
  async function save() {
    if (draft === undefined) return;
    const response = await mutation.run(() => hardwareApi.saveReview(productId, { body: draft, rating, ...(review ? { reviewVersion: review.version } : {}) }, review?.id));
    if (response) onClose(); else setDraft(undefined);
  }
  return <HardwareDialog title={t(review ? "reviews.edit" : "reviews.write")} onClose={onClose} busy={mutation.pending}>
    <form className="space-y-6" onSubmit={(event) => { event.preventDefault(); setDraft(String(new FormData(event.currentTarget).get("body") ?? "").trim()); }}>
      <div className="space-y-2 text-sm font-medium">
        <p>{t("reviews.rating")}</p>
        <StarRating
          value={rating}
          onChange={setRating}
          allowHalf={REVIEW_HALF_STARS}
          disabled={mutation.pending}
          label={t("reviews.ratingLabel")}
          emptyText={t("reviews.noRating")}
          formatValue={(value) => t("reviews.ratingValue", { rating: value })}
          clearLabel={t("reviews.clearRating")}
        />
      </div>
      <Field label={t("reviews.body")} hint={t("reviews.bodyHint")}>
        <textarea required name="body" maxLength={5000} minLength={1} defaultValue={review?.body} rows={6} className={`${ovalInput} resize-y`} onInput={(event) => event.currentTarget.setCustomValidity(event.currentTarget.value.trim() ? "" : t("common.required"))} />
      </Field>
      <HardwareError error={draft === undefined ? mutation.error : undefined} messages={review ? undefined : { 409: { title: t("reviews.duplicateTitle"), description: t("reviews.duplicateDescription") } }} />
      <div className="flex flex-wrap justify-end gap-2">
        <button className={ovalSecondary} type="button" disabled={mutation.pending} onClick={onClose}>{t("common.cancel")}</button>
        <button className={ovalPrimary} disabled={mutation.pending || mutation.requiresReview}>{t("common.save")}</button>
      </div>
    </form>
    {draft !== undefined ? <ConfirmDialog
      title={t("reviews.confirmTitle")}
      description={t(review ? "reviews.confirmUpdate" : "reviews.confirmCreate")}
      confirmLabel={t(mutation.pending ? "common.saving" : "reviews.confirmSave")}
      cancelLabel={t("reviews.confirmBack")}
      busy={mutation.pending}
      onConfirm={save}
      onCancel={() => setDraft(undefined)}
    /> : null}
  </HardwareDialog>;
}

export function ReportReviewForm({ reviewId, onClose }: { reviewId: string; onClose: () => void }) {
  const t = useTranslations("Hardware");
  const mutation = useHardwareMutation();
  return <HardwareDialog title={t("reviews.report")} onClose={onClose} busy={mutation.pending}>{mutation.success ? <HardwareSuccess>{t("reviews.reportSaved")}</HardwareSuccess> : <form className="space-y-5" onSubmit={async (event) => { event.preventDefault(); const form = new FormData(event.currentTarget); await mutation.run(() => hardwareApi.reportReview(reviewId, { reason: String(form.get("reason")), ...(form.get("details") ? { details: String(form.get("details")).trim() } : {}) })); }}>
    <Field label={t("reviews.reason")}>
      <select className={ovalInput} name="reason">{reportReasons.map((reason) => <option key={reason} value={reason}>{t(`reportReasons.${reason}`)}</option>)}</select>
    </Field>
    <Field label={t("reviews.details")}>
      <textarea className={`${ovalInput} resize-y`} name="details" maxLength={500} rows={4} />
    </Field>
    <HardwareError error={mutation.error} />
    <button className={ovalPrimary} disabled={mutation.pending || mutation.requiresReview}>{t("reviews.report")}</button>
  </form>}</HardwareDialog>;
}
