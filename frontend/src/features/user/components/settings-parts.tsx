"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { ApiHttpError } from "@/shared/lib/api/client";
import { cn } from "@/shared/utils/cn";

export const selectClass =
  "min-h-11 w-full rounded-control border border-control-border bg-surface px-3 text-text-primary";

const saveButtonClass =
  "inline-flex min-h-10 items-center justify-center rounded-control bg-primary px-5 text-sm font-semibold text-primary-foreground transition-colors duration-150 hover:bg-secondary aria-disabled:opacity-60";

export type SaveMessage = { kind: "ok" | "error"; key: string };

/** Hata kodunu kullanıcı mesajı anahtarına çevirir; teknik ayrıntı gösterilmez. */
export function settingsErrorKey(error: unknown): string {
  if (!(error instanceof ApiHttpError)) {
    return "generic";
  }
  if (error.status === 0) return "network";
  if (error.status === 409) return "conflict";
  if (error.status === 429) return "rateLimited";
  if (error.status === 503) return "busy";
  if (error.status === 400) return "invalid";
  return "generic";
}

/** Etiketli seçim kutusu: etiket, açıklama ve `select` birbirine `htmlFor`/`aria-describedby` ile bağlıdır. */
export function SelectRow({
  id,
  label,
  hint,
  value,
  options,
  disabled,
  onChange
}: {
  id: string;
  label: string;
  hint: string;
  value: string;
  options: ReadonlyArray<{ value: string; label: string }>;
  disabled?: boolean;
  onChange: (value: string) => void;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <select
        id={id}
        value={value}
        disabled={disabled}
        aria-describedby={`${id}-hint`}
        onChange={(event) => onChange(event.target.value)}
        className={cn(selectClass, "max-w-sm")}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      <p id={`${id}-hint`} className="text-xs text-text-secondary">
        {hint}
      </p>
    </div>
  );
}

/**
 * Kaydet düğmesi ve sonuç bildirimi. Başarı yalnız sunucu yanıtı gelince gösterilir; çakışmada (409) kullanıcıyı girdisi
 * silinmeden "Yenile" ile en güncel kayda yönlendirir.
 */
export function SaveBar({
  busy,
  message,
  onRefresh,
  children
}: {
  busy: boolean;
  message: SaveMessage | null;
  onRefresh: () => void;
  children?: ReactNode;
}) {
  const t = useTranslations("AccountSettings");
  const te = useTranslations("AccountSettings.errors");
  return (
    <div className="mt-5 flex flex-wrap items-center gap-3">
      <button type="submit" aria-disabled={busy} className={saveButtonClass}>
        {busy ? t("saving") : t("save")}
      </button>
      {children}
      <div aria-live="polite" className="min-h-6">
        {message ? (
          <p
            role={message.kind === "error" ? "alert" : "status"}
            className={cn("text-sm", message.kind === "ok" ? "text-success" : "text-brand-text")}
          >
            {message.kind === "ok" ? t("saved") : te(message.key)}
            {message.kind === "error" && message.key === "conflict" ? (
              <button type="button" onClick={onRefresh} className="ms-2 underline">
                {t("refresh")}
              </button>
            ) : null}
          </p>
        ) : null}
      </div>
    </div>
  );
}
