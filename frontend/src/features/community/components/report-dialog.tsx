"use client";

import { useId, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { useAction } from "../hooks/use-action";
import { reportComment, reportPost } from "../services/community-api";
import { statusOf } from "../services/community-errors";
import { LIMITS, validateText } from "../schemas/community.schema";
import { reportReasons, type ReportReason } from "../types/community.types";
import { ActionError, buttonClass, FieldError, inputClass, ModalDialog, primaryButtonClass } from "./community-ui";

export type ReportTarget = { kind: "post" | "comment"; id: string };

/**
 * İçerik raporu (COM-16). Gerekçe zorunlu, açıklama isteğe bağlı (≤500). Aynı içerik bir kez raporlanır (409) ve kendi
 * içeriğin raporlanamaz (422); bu durumlar ayrı, anlaşılır mesajlarla gösterilir. Başarı yalnız sunucu 201 döndüğünde yazılır.
 */
export function ReportDialog({ target, onClose }: { target: ReportTarget | null; onClose: () => void }) {
  return (
    <ReportDialogShell target={target} onClose={onClose} />
  );
}

function ReportDialogShell({ target, onClose }: { target: ReportTarget | null; onClose: () => void }) {
  const t = useTranslations("Community.report");
  return (
    <ModalDialog open={target !== null} onClose={onClose} title={target?.kind === "comment" ? t("titleComment") : t("titlePost")}>
      {target ? <ReportForm key={target.id} target={target} onClose={onClose} /> : null}
    </ModalDialog>
  );
}

function ReportForm({ target, onClose }: { target: ReportTarget; onClose: () => void }) {
  const t = useTranslations("Community");
  const formId = useId();
  const [reason, setReason] = useState<ReportReason | "">("");
  const [detail, setDetail] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [touched, setTouched] = useState(false);
  const send = useAction((chosen: ReportReason, text: string) =>
    target.kind === "post" ? reportPost(target.id, chosen, text) : reportComment(target.id, chosen, text)
  );

  const detailIssue = validateText(detail, LIMITS.reportDetail, { required: false });
  const reasonMissing = reason === "";

  async function submit(event: FormEvent) {
    event.preventDefault();
    setTouched(true);
    if (reasonMissing || detailIssue) return;
    const result = await send.run(reason, detail.trim());
    if (result.ok) setSubmitted(true);
  }

  if (submitted) {
    return (
      <div>
        <p role="status" className="text-sm leading-6 text-text-primary">
          {t("report.done")}
        </p>
        <div className="mt-5 flex justify-end">
          <button type="button" className={buttonClass} onClick={onClose}>
            {t("state.close")}
          </button>
        </div>
      </div>
    );
  }

  const status = statusOf(send.error);
  const specific = status === 409 ? t("report.already") : status === 422 ? t("report.own") : null;

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      <fieldset aria-describedby={touched && reasonMissing ? `${formId}-reason-error` : undefined}>
        <legend className="mb-2 text-sm font-medium text-text-primary">{t("report.reason")}</legend>
        <div className="grid gap-1.5">
          {reportReasons.map((value) => (
            <label key={value} className="flex min-h-11 cursor-pointer items-center gap-3 rounded-control border border-border px-3 text-sm has-[:checked]:border-primary has-[:checked]:bg-primary/10 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-focus">
              <input
                type="radio"
                name={`${formId}-reason`}
                value={value}
                checked={reason === value}
                onChange={() => setReason(value)}
                className="size-4 accent-primary"
              />
              {t(`report.reasons.${value}`)}
            </label>
          ))}
        </div>
        {touched && reasonMissing ? <FieldError id={`${formId}-reason-error`}>{t("report.reasonRequired")}</FieldError> : null}
      </fieldset>

      <div>
        <label htmlFor={`${formId}-detail`} className="mb-1.5 block text-sm font-medium text-text-primary">
          {t("report.detail")} <span className="font-normal text-text-secondary">({t("form.optional")})</span>
        </label>
        <textarea
          id={`${formId}-detail`}
          rows={3}
          value={detail}
          onChange={(event) => setDetail(event.target.value)}
          aria-invalid={detailIssue ? true : undefined}
          aria-describedby={detailIssue ? `${formId}-detail-error` : undefined}
          className={`${inputClass} resize-y`}
        />
        <p className="mt-1 text-xs text-text-secondary">{detail.trim().length}/{LIMITS.reportDetail}</p>
        {detailIssue ? <FieldError id={`${formId}-detail-error`}>{t(`form.error.${detailIssue}`, { max: LIMITS.reportDetail })}</FieldError> : null}
      </div>

      {specific ? (
        <p role="alert" className="rounded-control border border-warning/60 bg-warning/10 px-3 py-2 text-sm text-text-primary">
          {specific}
        </p>
      ) : (
        <ActionError error={send.error} />
      )}

      <div className="flex flex-wrap justify-end gap-3">
        <button type="button" className={buttonClass} onClick={onClose} disabled={send.pending}>
          {t("state.cancel")}
        </button>
        <button type="submit" className={primaryButtonClass} disabled={send.pending}>
          {send.pending ? t("state.working") : t("report.submit")}
        </button>
      </div>
    </form>
  );
}
