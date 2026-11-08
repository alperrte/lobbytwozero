"use client";

import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { ApiHttpError } from "@/shared/lib/api/client";
import { cn } from "@/shared/utils/cn";
import { errorKeyOf, type CommunityErrorKey } from "../services/community-errors";
import type { AuthorCard } from "../types/community.types";

/* Yalnız semantik tokenlar (globals.css). Yarıçap ve renkler ortak sistemden gelir; Community'ye özel palet yoktur. */
const focusRing = "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus";
export const panel = "rounded-panel border border-border bg-surface";
export const inputClass = `min-h-11 w-full min-w-0 rounded-control border border-control-border bg-background px-3 py-2 text-sm text-text-primary placeholder:text-text-secondary disabled:opacity-60 ${focusRing}`;
const buttonBase = `inline-flex min-h-11 items-center justify-center gap-2 rounded-control border px-4 py-2 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${focusRing}`;
export const buttonClass = `${buttonBase} border-control-border bg-surface text-text-primary hover:bg-surface-elevated`;
export const primaryButtonClass = `${buttonBase} border-primary bg-primary text-primary-foreground hover:bg-secondary hover:border-secondary`;
export const dangerButtonClass = `${buttonBase} border-primary/60 bg-primary/10 text-brand-text hover:bg-primary/20`;
/** Satır içi eylem (beğen, yanıtla…): metin + ikon, en az 44px dokunma hedefi. */
export const actionClass = `inline-flex min-h-11 items-center gap-1.5 rounded-control px-2.5 text-sm font-medium text-text-secondary transition-colors hover:bg-surface-elevated hover:text-text-primary disabled:cursor-not-allowed disabled:opacity-50 ${focusRing}`;
export const chipClass = "inline-flex max-w-full items-center gap-1.5 rounded-control border border-border bg-surface-elevated px-2 py-0.5 text-xs font-medium text-text-secondary";

/** Başlık sol çizgisi tüm LTZ ekranlarında aynıdır (Hardware ile aynı kimlik). */
export function CommunityHeading({ title, description, children }: { title: string; description: string; children?: ReactNode }) {
  return (
    <header className="mb-5 flex flex-wrap items-start justify-between gap-4">
      <div className="max-w-2xl">
        <div aria-hidden="true" className="mb-3 h-1 w-10 bg-primary" />
        <h1 className="font-display text-3xl font-bold tracking-tight text-text-primary sm:text-4xl">{title}</h1>
        <p className="mt-2 text-sm leading-6 text-text-secondary">{description}</p>
      </div>
      {children}
    </header>
  );
}

/* ----------------------------- Avatar ve zaman ---------------------------- */

function initialsOf(label: string): string {
  const parts = label.trim().split(/\s+/).filter(Boolean);
  const letters = parts.length > 1 ? [parts[0], parts[parts.length - 1]] : [parts[0] ?? ""];
  return letters.map((part) => [...part][0] ?? "").join("").toLocaleUpperCase() || "?";
}

export function authorLabel(author: AuthorCard | null, fallback: string): string {
  return author?.displayName?.trim() || author?.username?.trim() || fallback;
}

export function Avatar({ author, label, size = "md" }: { author: AuthorCard | null; label: string; size?: "sm" | "md" | "lg" }) {
  const box = size === "lg" ? "size-16 text-xl" : size === "sm" ? "size-8 text-xs" : "size-11 text-sm";
  return (
    <span
      aria-hidden="true"
      className={cn(
        "inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full border-2 border-primary/70 bg-surface-elevated font-display font-semibold text-text-primary",
        box
      )}
    >
      {author?.avatarUrl ? (
        // Kullanıcı medya adresi doğrulanmış Gateway yoludur; boyutu CSS ile sabit, optimizasyon vekili gerekmez.
        // eslint-disable-next-line @next/next/no-img-element
        <img src={author.avatarUrl} alt="" className="size-full object-cover" referrerPolicy="no-referrer" loading="lazy" />
      ) : (
        initialsOf(label)
      )}
    </span>
  );
}

const UNITS: ReadonlyArray<[Intl.RelativeTimeFormatUnit, number]> = [
  ["year", 31_536_000],
  ["month", 2_592_000],
  ["day", 86_400],
  ["hour", 3600],
  ["minute", 60]
];

/** "2 saat önce" biçimi; bir haftadan eski içerikte mutlak tarih. Tam tarih `title` ve `dateTime` içindedir. */
export function RelativeTime({ value, className }: { value: string; className?: string }) {
  const locale = useLocale();
  const [now] = useState(() => Date.now());
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  const seconds = Math.round((date.getTime() - now) / 1000);
  const absolute = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(date);
  let text: string;
  if (Math.abs(seconds) > 7 * 86_400) {
    text = new Intl.DateTimeFormat(locale, { dateStyle: "medium" }).format(date);
  } else {
    const [unit, size] = UNITS.find(([, span]) => Math.abs(seconds) >= span) ?? (["minute", 60] as const);
    const formatter = new Intl.RelativeTimeFormat(locale, { numeric: "auto" });
    text = Math.abs(seconds) < 60 ? formatter.format(0, "second") : formatter.format(Math.round(seconds / size), unit);
  }
  return (
    <time dateTime={date.toISOString()} title={absolute} className={className}>
      {text}
    </time>
  );
}

/* ------------------------------- Durum panelleri --------------------------- */

export function LoadingBlock({ rows = 3 }: { rows?: number }) {
  const t = useTranslations("Community.state");
  return (
    <div role="status" className={cn(panel, "p-5")}>
      <p className="sr-only">{t("loading")}</p>
      <div aria-hidden="true" className="space-y-4 motion-safe:animate-pulse">
        {Array.from({ length: rows }, (_, index) => (
          <div key={index} className="flex gap-3">
            <div className="size-11 shrink-0 rounded-full bg-surface-elevated" />
            <div className="flex-1 space-y-2.5 pt-1">
              <div className="h-3.5 w-1/3 rounded-control bg-surface-elevated" />
              <div className="h-3.5 w-full rounded-control bg-surface-elevated" />
              <div className="h-3.5 w-2/3 rounded-control bg-surface-elevated" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export function EmptyBlock({ title, description, children }: { title: string; description?: string; children?: ReactNode }) {
  return (
    <div className={cn(panel, "flex flex-col items-center px-6 py-12 text-center")}>
      <span className="mb-4 flex size-12 items-center justify-center rounded-panel border border-border bg-surface-elevated text-text-secondary">
        <Icon name="messages" className="size-6" />
      </span>
      <h2 className="font-display text-lg font-semibold text-text-primary">{title}</h2>
      {description ? <p className="mt-2 max-w-md text-sm leading-6 text-text-secondary">{description}</p> : null}
      {children ? <div className="mt-5">{children}</div> : null}
    </div>
  );
}

/** Hata açıklaması: yalnız durum koduna göre çevrilmiş metin; sunucu metni gösterilmez. 429'da bekleme süresi yazılır. */
export function describeError(t: ReturnType<typeof useTranslations>, error: unknown): string {
  const key: CommunityErrorKey = errorKeyOf(error);
  const base = t(`errors.${key}`);
  if (key === "rateLimited" && error instanceof ApiHttpError && error.retryAfterSeconds) {
    return `${base} ${t("errors.retryAfter", { minutes: Math.max(1, Math.ceil(error.retryAfterSeconds / 60)) })}`;
  }
  return base;
}

export function ErrorBlock({ error, retry }: { error: unknown; retry?: () => void }) {
  const t = useTranslations("Community");
  if (!error) return null;
  return (
    <div role="alert" className={cn(panel, "border-s-2 border-s-warning p-5")}>
      <p className="font-display font-semibold text-text-primary">{t("state.errorTitle")}</p>
      <p className="mt-1 text-sm leading-6 text-text-secondary">{describeError(t, error)}</p>
      {retry ? (
        <button type="button" className={cn(buttonClass, "mt-4")} onClick={retry}>
          {t("state.retry")}
        </button>
      ) : null}
    </div>
  );
}

/** Eylem hatası (mutasyon): form altında, alan hatalarından ayrı. Başarısız istekte başarı metni asla gösterilmez. */
export function ActionError({ error }: { error: unknown }) {
  const t = useTranslations("Community");
  if (!error) return null;
  return (
    <p role="alert" className="rounded-control border border-warning/60 bg-warning/10 px-3 py-2 text-sm text-text-primary">
      {describeError(t, error)}
    </p>
  );
}

export function FieldError({ id, children }: { id: string; children: ReactNode }) {
  return (
    <p id={id} role="alert" className="mt-1.5 text-sm text-brand-text">
      {children}
    </p>
  );
}

export function LoadMore({ hasMore, loading, error, onMore }: { hasMore: boolean; loading: boolean; error?: unknown; onMore: () => void }) {
  const t = useTranslations("Community.state");
  if (!hasMore && !error) return null;
  return (
    <div className="mt-4 flex flex-col items-center gap-3">
      {error ? <ActionError error={error} /> : null}
      <button type="button" className={buttonClass} onClick={onMore} disabled={loading}>
        {loading ? t("loadingMore") : error ? t("retry") : t("loadMore")}
      </button>
    </div>
  );
}

/* --------------------------------- Diyalog --------------------------------- */

/**
 * Yerel `<dialog>` kipi: odak diyalog içinde tutulur, arka plan etkisizleşir, Escape kapatır ve odak tetikleyiciye döner.
 * İçerik yalnız açıkken kurulur; böylece her açılışta form durumu sıfırlanır.
 */
export function ModalDialog({ open, onClose, title, children, wide = false }: { open: boolean; onClose: () => void; title: string; children: ReactNode; wide?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const t = useTranslations("Community.state");

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onClose={onClose}
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
      className={cn(
        "m-auto max-h-[calc(100dvh-1rem)] overflow-y-auto rounded-panel border border-border bg-surface p-0 text-text-primary shadow-popover backdrop:bg-scrim",
        wide ? "w-[min(64rem,calc(100vw-1rem))]" : "w-[min(34rem,calc(100vw-2rem))] max-h-[calc(100dvh-2rem)]"
      )}
    >
      {open ? (
        <div className={wide ? "p-3 sm:p-4" : "p-5 sm:p-6"}>
          <div className="mb-4 flex items-start justify-between gap-4">
            <h2 id={titleId} className="font-display text-xl font-semibold">
              {title}
            </h2>
            <button type="button" onClick={onClose} aria-label={t("close")} className={cn(actionClass, "-m-2 shrink-0")}>
              <Icon name="close" />
            </button>
          </div>
          {children}
        </div>
      ) : null}
    </dialog>
  );
}

/** Geri alınamaz eylem onayı (silme). Eylem sürerken iptal ve onay kilitlenir; hata diyalogda kalır. */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  pending,
  error,
  onConfirm,
  onClose
}: {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  pending: boolean;
  error?: unknown;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const t = useTranslations("Community.state");
  return (
    <ModalDialog open={open} onClose={() => !pending && onClose()} title={title}>
      <p className="text-sm leading-6 text-text-secondary">{description}</p>
      <div className="mt-4">
        <ActionError error={error} />
      </div>
      <div className="mt-5 flex flex-wrap justify-end gap-3">
        <button type="button" className={buttonClass} onClick={onClose} disabled={pending}>
          {t("cancel")}
        </button>
        <button type="button" className={dangerButtonClass} onClick={onConfirm} disabled={pending}>
          {pending ? t("working") : confirmLabel}
        </button>
      </div>
    </ModalDialog>
  );
}
