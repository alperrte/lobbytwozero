"use client";

import { cloneElement, useEffect, useId, useRef, type ReactElement, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { ApiHttpError } from "@/shared/lib/api/client";
import { lockPageScroll } from "@/shared/lib/ui/scroll-lock";

export const panel = "rounded-panel border border-border bg-surface";
export const ovalPanel = "rounded-2xl border border-border bg-surface";
export const input = "min-h-11 w-full min-w-0 rounded-control border border-control-border bg-background px-3 py-2 text-sm text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus disabled:opacity-60";
const controlButton = "inline-flex min-h-11 items-center justify-center gap-2 rounded-control border px-4 py-2 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-50";
export const button = `${controlButton} border-control-border bg-surface text-text-primary hover:bg-surface-elevated`;
export const primaryButton = `${controlButton} border-primary bg-primary text-primary-foreground hover:bg-secondary`;
export const subtleButton = `${button} border-border`;
const ovalControl = "inline-flex min-h-11 items-center justify-center gap-2 rounded-xl border px-5 py-2 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-50";
export const ovalButton = `${ovalControl} border-control-border bg-surface text-text-primary hover:bg-surface-elevated`;
export const ovalPrimaryButton = `${ovalControl} border-primary bg-primary text-primary-foreground hover:bg-secondary`;

export function HardwareHeading({ title, description, children }: { title: string; description: string; children?: ReactNode }) {
  return <header className="mb-7 flex flex-wrap items-end justify-between gap-4 border-b border-border pb-6">
    <div className="max-w-2xl">
      <div className="mb-3 h-1 w-10 bg-primary" />
      <h1 className="font-display text-3xl font-bold tracking-tight sm:text-4xl">{title}</h1>
      <p className="mt-2 text-sm leading-6 text-text-secondary">{description}</p>
    </div>
    {children}
  </header>;
}
export function HardwareEmpty({ title, description }: { title: string; description: string }) {
  return <div className={`${panel} flex min-h-64 flex-col items-center justify-center px-6 py-10 text-center`}>
    <span className="mb-5 flex size-14 items-center justify-center rounded-panel border border-border bg-surface-elevated">
      <Icon name="cpu" className="size-7 text-text-secondary" />
    </span>
    <h2 className="font-display text-xl font-semibold">{title}</h2>
    <p className="mt-2 max-w-md text-sm leading-6 text-text-secondary">{description}</p>
  </div>;
}
export function HardwareLoading() {
  const t = useTranslations("Hardware.common");
  return <div role="status" className={`${panel} p-6`}>
    <p className="mb-5 text-sm text-text-secondary">{t("loading")}</p>
    <div aria-hidden="true" className="space-y-4 motion-safe:animate-pulse">{[1, 2, 3].map((key) => <div key={key} className="h-8 rounded-control bg-surface-elevated" />)}</div>
  </div>;
}
/** `messages`: belirli durum kodları için varsayılan metni geçersiz kılar (ör. yinelenen değerlendirme için 409). */
export function HardwareError({ error, retry, messages }: { error: unknown; retry?: () => void; messages?: Partial<Record<number, { title: string; description: string }>> }) {
  const t = useTranslations("Hardware");
  if (!error) return null;
  const status = error instanceof ApiHttpError ? error.status : 0;
  const key = [400, 401, 403, 404, 409, 413, 422, 429, 500, 503].includes(status) ? String(status) : "network";
  const custom = messages?.[status];
  return <div role="alert" className={`${panel} border-s-warning border-s-2 p-5`}>
    <p className="font-display font-semibold">{custom ? custom.title : t(`errors.${key}.title`)}</p>
    <p className="mt-1 text-sm leading-6 text-text-secondary">{custom ? custom.description : t(`errors.${key}.description`)}</p>
    {retry ? <button type="button" className={`${subtleButton} mt-4`} onClick={retry}>{t("common.reload")}</button> : null}
  </div>;
}
export function HardwareSuccess({ children }: { children: ReactNode }) {
  return <p role="status" className="rounded-control border border-success/50 bg-success/10 p-3 text-sm text-success">{children}</p>;
}
export function HardwarePagination({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (page: number) => void }) {
  const t = useTranslations("Hardware.common");
  if (totalPages < 2) return null;
  return <nav aria-label={t("pagination")} className="mt-5 flex flex-wrap items-center justify-between gap-3">
    <button type="button" className={button} disabled={page === 0} onClick={() => onPage(page - 1)}>{t("previous")}</button>
    <span className="text-sm text-text-secondary">{t("page", { page: page + 1, total: totalPages })}</span>
    <button type="button" className={button} disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>{t("next")}</button>
  </nav>;
}
export function HardwareDialog({ title, children, onClose, busy = false, wide = false }: { title: string; children: ReactNode; onClose: () => void; busy?: boolean; wide?: boolean }) {
  const t = useTranslations("Hardware.common");
  const titleId = useId();
  const ref = useRef<HTMLDialogElement>(null);
  const closeRef = useRef(onClose);
  useEffect(() => { closeRef.current = onClose; });
  useEffect(() => {
    const dialog = ref.current;
    const previous = document.activeElement as HTMLElement | null;
    dialog?.showModal();
    const unlock = lockPageScroll();
    return () => { dialog?.close(); unlock(); previous?.focus(); };
  }, []);
  return <dialog ref={ref} aria-labelledby={titleId} aria-busy={busy} onCancel={(event) => { event.preventDefault(); if (!busy) closeRef.current(); }} className={`fixed inset-0 m-auto max-h-[90dvh] w-[calc(100%-2rem)] ${wide ? "max-w-5xl" : "max-w-2xl"} overflow-y-auto overscroll-contain rounded-[22px] border border-border bg-surface p-0 text-text-primary shadow-popover backdrop:bg-scrim backdrop:backdrop-blur-[2px]`}>
    <div className="flex items-start justify-between gap-4 border-b border-border px-6 py-5">
      <h2 id={titleId} className="font-display text-xl font-semibold">{title}</h2>
      <button type="button" disabled={busy} className={`${button} -me-2 -mt-1 size-11 rounded-full p-2`} aria-label={t("close")} onClick={onClose}>
        <Icon name="close" />
      </button>
    </div>
    <div className="space-y-5 p-6">{children}</div>
  </dialog>;
}
export function Field({ label, children, hint }: { label: string; children: ReactElement<{ id?: string; "aria-describedby"?: string }>; hint?: string }) {
  const id = useId();
  return <div className="min-w-0 space-y-1.5 text-sm font-medium">
    <label htmlFor={id} className="block">{label}</label>
    {cloneElement(children, { id, "aria-describedby": hint ? `${id}-hint` : undefined })}
    {hint ? <p id={`${id}-hint`} className="text-xs font-normal leading-5 text-text-secondary">{hint}</p> : null}
  </div>;
}
