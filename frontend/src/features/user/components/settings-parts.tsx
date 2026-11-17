"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { ApiHttpError } from "@/shared/lib/api/client";
import { cn } from "@/shared/utils/cn";

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

/**
 * Etiketli seçenek grubu: tarayıcının ham `select`i yerine tüm seçenekler tek bakışta görünen kartlardır. Altta gerçek
 * `radio` düğmeleri vardır (ok tuşları, odak ve ekran okuyucu yerleşik çalışır); grup adı etiketten, açıklaması ipucundan gelir.
 */
export function ChoiceRow({
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
    <div className="flex flex-col gap-2">
      <span id={`${id}-label`} className="text-sm font-medium">
        {label}
      </span>
      <div
        role="radiogroup"
        aria-labelledby={`${id}-label`}
        aria-describedby={`${id}-hint`}
        className="grid max-w-3xl gap-2 [grid-template-columns:repeat(auto-fit,minmax(11rem,1fr))]"
      >
        {options.map((option) => {
          const on = option.value === value;
          return (
            <label
              key={option.value}
              className={cn(
                "flex min-h-11 items-center gap-2.5 rounded-control border px-3 py-2 text-sm transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-focus",
                on
                  ? "border-primary bg-primary/10 text-text-primary"
                  : "border-control-border bg-surface text-text-secondary hover:bg-surface-elevated hover:text-text-primary",
                disabled ? "cursor-not-allowed opacity-60" : "cursor-pointer"
              )}
            >
              <input
                type="radio"
                name={id}
                value={option.value}
                checked={on}
                disabled={disabled}
                onChange={() => onChange(option.value)}
                className="sr-only"
              />
              <span
                aria-hidden="true"
                className={cn("grid size-4 shrink-0 place-items-center rounded-full border", on ? "border-primary" : "border-control-border")}
              >
                {on ? <span className="size-2 rounded-full bg-primary" /> : null}
              </span>
              <span className="leading-5">{option.label}</span>
            </label>
          );
        })}
      </div>
      <p id={`${id}-hint`} className="text-xs text-text-secondary">
        {hint}
      </p>
    </div>
  );
}

/**
 * Açma/kapama satırı: ham onay kutusu yerine anahtar (switch). Altta `role="switch"` onay kutusu durur ve anahtarın
 * üstünü örter; tıklama, boşluk tuşu ve ekran okuyucu bildirimi yerleşik çalışır. `status` anahtarı yinelemez, ek bilgi verir.
 */
export function SwitchRow({
  id,
  label,
  hint,
  status,
  checked,
  disabled,
  onChange
}: {
  id: string;
  label: string;
  hint: string;
  status?: string;
  checked: boolean;
  disabled?: boolean;
  onChange: (next: boolean) => void;
}) {
  return (
    <div className="flex items-start justify-between gap-4 rounded-control border border-border bg-surface-elevated p-4">
      <div className="flex min-w-0 flex-col gap-1">
        <label htmlFor={id} className="text-sm font-medium">
          {label}
        </label>
        <p id={`${id}-hint`} className="text-xs text-text-secondary">
          {hint}
        </p>
        {status ? <p className="text-xs text-text-secondary">{status}</p> : null}
      </div>
      <span className="relative mt-0.5 inline-flex shrink-0">
        <input
          id={id}
          type="checkbox"
          role="switch"
          checked={checked}
          disabled={disabled}
          aria-describedby={`${id}-hint`}
          onChange={(event) => onChange(event.target.checked)}
          className="peer absolute inset-0 z-10 size-full cursor-pointer opacity-0 disabled:cursor-not-allowed"
        />
        <span
          aria-hidden="true"
          className="block h-6 w-11 rounded-full border border-control-border bg-surface transition-colors peer-checked:border-primary peer-checked:bg-primary peer-disabled:opacity-60 peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-focus"
        />
        <span
          aria-hidden="true"
          className="pointer-events-none absolute left-0.5 top-0.5 size-5 rounded-full bg-text-secondary transition-transform peer-checked:translate-x-5 peer-checked:bg-primary-foreground"
        />
      </span>
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
