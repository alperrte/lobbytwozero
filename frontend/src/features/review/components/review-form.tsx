"use client";

import { useId, useRef, useState, type FormEvent, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { REVIEW_BODY_MAX, reviewBodyIssue } from "../schemas/review-schema";
import { actionClass } from "./review-ui";

/**
 * İnceleme metni formu (yazma ve düzenleme ortak). Metin kontrolü üst bileşende tutulur; hata sonrası girdi KORUNUR.
 * Sayaç ham UTF-16 uzunluğunu sayar (backend sınırı boşluk temizlenmeden önce 5000'dir). Gönderim sırasında düğme kapanır.
 */
export function ReviewForm({ value, onChange, onSubmit, pending, submitLabel, pendingLabel, disabledReason, onCancel, children }: { value: string; onChange: (value: string) => void; onSubmit: () => void; pending: boolean; submitLabel: string; pendingLabel: string; disabledReason?: string; onCancel?: () => void; children?: ReactNode }) {
  const t = useTranslations("Review");
  const id = useId();
  const field = useRef<HTMLTextAreaElement>(null);
  const [attempted, setAttempted] = useState(false);
  const issue = reviewBodyIssue(value);
  const visibleIssue = issue === "tooLong" || (attempted && issue !== null) ? issue : null;
  const over = value.length > REVIEW_BODY_MAX;

  function submit(event: FormEvent) {
    event.preventDefault();
    if (pending || disabledReason) return;
    setAttempted(true);
    if (issue !== null) {
      field.current?.focus();
      return;
    }
    onSubmit();
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      <div className="space-y-2">
        <label htmlFor={`${id}-body`} className="block text-sm font-semibold">{t("form.label")}</label>
        <textarea
          id={`${id}-body`}
          ref={field}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          rows={10}
          disabled={pending}
          aria-invalid={visibleIssue !== null}
          aria-describedby={`${id}-help ${id}-count${visibleIssue ? ` ${id}-error` : ""}`}
          className="block min-h-48 w-full resize-y rounded-control border border-control-border bg-surface-elevated p-3 text-sm leading-6 text-text-primary placeholder:text-text-secondary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus disabled:opacity-70 aria-invalid:border-primary"
          placeholder={t("form.placeholder")}
        />
        <div className="flex flex-wrap items-start justify-between gap-2 text-xs text-text-secondary">
          <p id={`${id}-help`}>{t("form.help")}</p>
          <p id={`${id}-count`} className={over ? "font-semibold text-brand-text" : "tabular-nums"}>
            {t("form.counter", { count: value.length, max: REVIEW_BODY_MAX })}
          </p>
        </div>
        {visibleIssue ? <p id={`${id}-error`} role="alert" className="text-sm text-brand-text">{t(`form.issue.${visibleIssue}`, { max: REVIEW_BODY_MAX })}</p> : null}
      </div>
      {children}
      {disabledReason ? <p role="status" className="text-sm text-text-secondary">{disabledReason}</p> : null}
      <div className="flex flex-wrap items-center gap-2">
        <button type="submit" disabled={pending || Boolean(disabledReason)} aria-busy={pending} className={actionClass("primary")}>
          {pending ? pendingLabel : submitLabel}
        </button>
        {onCancel ? <button type="button" onClick={onCancel} disabled={pending} className={actionClass("ghost")}>{t("cancel")}</button> : null}
      </div>
    </form>
  );
}
