"use client";
import { useTranslations } from "next-intl";
import { useHardwareMutation } from "../hooks/use-hardware-query";
import { hardwareApi } from "../services/hardware-api";
import { reportReasons, type Review } from "../types/hardware";
import { button, primaryButton, input, Field, HardwareDialog, HardwareError, HardwareSuccess } from "./hardware-ui";

export function ReviewForm({ productId, review, onClose, onSaved }: { productId: string; review?: Review; onClose: () => void; onSaved: () => void }) {
  const t = useTranslations("Hardware");
  const mutation = useHardwareMutation(onSaved);
  return <HardwareDialog title={t(review ? "reviews.edit" : "reviews.write")} onClose={onClose} busy={mutation.pending}>
    <form className="space-y-5" onSubmit={async (event) => { event.preventDefault(); const form = new FormData(event.currentTarget); const response = await mutation.run(() => hardwareApi.saveReview(productId, { body: String(form.get("body")).trim(), rating: form.get("rating") ? Number(form.get("rating")) : null, ...(review ? { reviewVersion: review.version } : {}) }, review?.id)); if (response) onClose(); }}>
      <Field label={t("reviews.rating")}>
        <select name="rating" className={input} defaultValue={review?.rating ?? ""}>
          <option value="">{t("reviews.noRating")}</option>
          {[1, 2, 3, 4, 5].map((rating) => <option key={rating} value={rating}>{t("reviews.ratingValue", { rating })}</option>)}
        </select>
      </Field>
      <Field label={t("reviews.body")} hint={t("reviews.bodyHint")}>
        <textarea required name="body" maxLength={5000} minLength={1} defaultValue={review?.body} rows={6} className={input} onInput={(event) => event.currentTarget.setCustomValidity(event.currentTarget.value.trim() ? "" : t("common.required"))} />
      </Field>
      <HardwareError error={mutation.error} />
      <div className="flex flex-wrap justify-end gap-2">
        <button className={button} type="button" disabled={mutation.pending} onClick={onClose}>{t("common.cancel")}</button>
        <button className={primaryButton} disabled={mutation.pending || mutation.requiresReview}>{t(mutation.pending ? "common.saving" : "common.save")}</button>
      </div>
    </form>
  </HardwareDialog>;
}

export function ReportReviewForm({ reviewId, onClose }: { reviewId: string; onClose: () => void }) {
  const t = useTranslations("Hardware");
  const mutation = useHardwareMutation();
  return <HardwareDialog title={t("reviews.report")} onClose={onClose} busy={mutation.pending}>{mutation.success ? <HardwareSuccess>{t("reviews.reportSaved")}</HardwareSuccess> : <form className="space-y-5" onSubmit={async (event) => { event.preventDefault(); const form = new FormData(event.currentTarget); await mutation.run(() => hardwareApi.reportReview(reviewId, { reason: String(form.get("reason")), ...(form.get("details") ? { details: String(form.get("details")).trim() } : {}) })); }}>
    <Field label={t("reviews.reason")}>
      <select className={input} name="reason">{reportReasons.map((reason) => <option key={reason} value={reason}>{t(`reportReasons.${reason}`)}</option>)}</select>
    </Field>
    <Field label={t("reviews.details")}>
      <textarea className={input} name="details" maxLength={500} rows={4} />
    </Field>
    <HardwareError error={mutation.error} />
    <button className={primaryButton} disabled={mutation.pending || mutation.requiresReview}>{t("reviews.report")}</button>
  </form>}</HardwareDialog>;
}
