"use client";

import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { useLocale, useTranslations } from "next-intl";
import { lockPageScroll } from "@/shared/lib/ui/scroll-lock";
import { useRetryCountdown } from "../hooks/use-retry-countdown";
import { useReviewAction } from "../hooks/use-review-action";
import { REPORT_DETAIL_MAX, REPORT_REASONS, isReportReason, reportDetailIssue } from "../schemas/review-schema";
import { reviewMutations } from "../services/review-api";
import { describeReviewError, retryAfterOf } from "../services/review-errors";
import { formatDateTime } from "../services/format";
import type { ReviewReport } from "../types/review";
import { ReviewAlert, actionClass, focusRing } from "./review-ui";

/**
 * İnceleme raporlama (REV-07, Review tarafı). Raporun YEREL olarak kaydedildiğini söyler; Admin'e iletildiğini veya
 * karara bağlandığını iddia etmez (teslim henüz yoktur). Kendi incelemende görünmez; mükerrer rapor (409) "zaten raporladın"
 * olarak ele alınır. Raportör kimliği inceleme sahibine gösterilmez. `initialReport`: sunucudan okunan mevcut rapor.
 */
export function ReportReview({ reviewId, ownedByActor, initialReport }: { reviewId: string; ownedByActor: boolean; initialReport?: ReviewReport | null }) {
  if (ownedByActor) return null;
  return <ReportControl reviewId={reviewId} initialReport={initialReport ?? null} />;
}

function ReportControl({ reviewId, initialReport }: { reviewId: string; initialReport: ReviewReport | null }) {
  const t = useTranslations("Review");
  const locale = useLocale();
  const [open, setOpen] = useState(false);
  const [saved, setSaved] = useState<ReviewReport | "duplicate" | null>(null);
  const report = saved ?? initialReport;

  if (report) {
    const when = typeof report === "object" ? formatDateTime(report.createdAt, locale) : null;
    return (
      <p role="status" className="inline-flex min-h-11 items-center text-sm text-text-secondary">
        {report === "duplicate" ? t("report.already") : when ? t("report.doneAt", { date: when }) : t("report.done")}
      </p>
    );
  }
  return (
    <>
      <button type="button" onClick={() => setOpen(true)} aria-haspopup="dialog" className={actionClass("ghost")}>{t("report.open")}</button>
      {open ? (
        <ReportDialog
          reviewId={reviewId}
          onClose={() => setOpen(false)}
          onReported={(value) => {
            setSaved(value);
            setOpen(false);
          }}
        />
      ) : null}
    </>
  );
}

function ReportDialog({ reviewId, onClose, onReported }: { reviewId: string; onClose: () => void; onReported: (value: ReviewReport | "duplicate") => void }) {
  const t = useTranslations("Review");
  const id = useId();
  const dialog = useRef<HTMLDialogElement>(null);
  const select = useRef<HTMLSelectElement>(null);
  const action = useReviewAction();
  const countdown = useRetryCountdown();
  const [reason, setReason] = useState("");
  const [detail, setDetail] = useState("");
  const [attempted, setAttempted] = useState(false);
  const closeHandler = useRef(onClose);
  useEffect(() => {
    closeHandler.current = onClose;
  });

  useEffect(() => {
    const element = dialog.current;
    const previous = document.activeElement as HTMLElement | null;
    element?.showModal();
    const unlock = lockPageScroll();
    select.current?.focus();
    return () => {
      element?.close();
      unlock();
      previous?.focus();
    };
  }, []);

  const detailIssue = reportDetailIssue(detail);
  const reasonMissing = !isReportReason(reason);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (action.pending || countdown.remaining > 0) return;
    setAttempted(true);
    if (!isReportReason(reason) || detailIssue !== null) {
      if (!isReportReason(reason)) select.current?.focus();
      return;
    }
    const result = await action.run((signal) => reviewMutations.reportReview(reviewId, reason, detail, signal));
    if (!result) return;
    if (result.ok) {
      onReported(result.data);
      return;
    }
    if (describeReviewError(result.error, "report").key === "409report") {
      onReported("duplicate");
      return;
    }
    const wait = retryAfterOf(result.error);
    if (wait) {
      countdown.start(wait);
      action.clearError();
    }
  }

  const blocked = countdown.remaining > 0;
  return (
    <dialog
      ref={dialog}
      aria-labelledby={`${id}-title`}
      aria-busy={action.pending}
      onCancel={(event) => {
        event.preventDefault();
        if (!action.pending) closeHandler.current();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget && !action.pending) closeHandler.current();
      }}
      className="fixed inset-0 m-auto w-[calc(100%-2rem)] max-w-lg overflow-visible rounded-[22px] border border-border bg-surface p-0 text-text-primary shadow-popover backdrop:bg-scrim backdrop:backdrop-blur-[2px]"
    >
      <form onSubmit={submit} noValidate className="space-y-4 p-6">
        <div className="space-y-1.5">
          <h2 id={`${id}-title`} className="font-display text-lg font-semibold">{t("report.title")}</h2>
          <p className="text-sm leading-6 text-text-secondary">{t("report.description")}</p>
        </div>

        <div className="space-y-1">
          <label htmlFor={`${id}-reason`} className="block text-sm font-semibold">{t("report.reasonLabel")}</label>
          <select
            id={`${id}-reason`}
            ref={select}
            value={reason}
            onChange={(event) => {
              setReason(event.target.value);
              action.clearError();
            }}
            disabled={action.pending}
            aria-invalid={attempted && reasonMissing}
            aria-describedby={attempted && reasonMissing ? `${id}-reason-error` : undefined}
            className={`block min-h-11 w-full rounded-control border border-control-border bg-surface-elevated px-3 text-sm text-text-primary aria-invalid:border-primary ${focusRing}`}
          >
            <option value="">{t("report.reasonPlaceholder")}</option>
            {REPORT_REASONS.map((value) => <option key={value} value={value}>{t(`report.reasons.${value}`)}</option>)}
          </select>
          {attempted && reasonMissing ? <p id={`${id}-reason-error`} role="alert" className="text-sm text-brand-text">{t("report.reasonRequired")}</p> : null}
        </div>

        <div className="space-y-1">
          <label htmlFor={`${id}-detail`} className="block text-sm font-semibold">{t("report.detailLabel")}</label>
          <textarea
            id={`${id}-detail`}
            value={detail}
            onChange={(event) => {
              setDetail(event.target.value);
              action.clearError();
            }}
            rows={4}
            disabled={action.pending}
            aria-invalid={detailIssue !== null}
            aria-describedby={`${id}-count`}
            className={`block w-full resize-y rounded-control border border-control-border bg-surface-elevated p-3 text-sm leading-6 text-text-primary aria-invalid:border-primary ${focusRing}`}
          />
          <p id={`${id}-count`} className={detail.length > REPORT_DETAIL_MAX ? "text-xs font-semibold text-brand-text" : "text-xs tabular-nums text-text-secondary"}>
            {t("report.counter", { count: detail.length, max: REPORT_DETAIL_MAX })}
          </p>
          {detailIssue ? <p role="alert" className="text-sm text-brand-text">{t(`report.issue.${detailIssue}`, { max: REPORT_DETAIL_MAX })}</p> : null}
        </div>

        <p className="text-xs leading-5 text-text-secondary">{t("report.privacy")}</p>
        {blocked ? <p role="status" className="text-sm text-text-secondary">{t("like.wait", { seconds: countdown.remaining })}</p> : null}
        <ReviewAlert error={action.error} context="report" />

        <div className="flex flex-wrap justify-end gap-2">
          <button type="button" onClick={onClose} disabled={action.pending} className={actionClass("ghost")}>{t("cancel")}</button>
          <button type="submit" disabled={action.pending || blocked} aria-busy={action.pending} className={actionClass("primary")}>
            {action.pending ? t("saving") : t("report.submit")}
          </button>
        </div>
      </form>
    </dialog>
  );
}
