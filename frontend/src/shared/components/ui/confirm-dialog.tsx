"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";
import { Icon } from "@/shared/components/ui/icons";
import { lockPageScroll } from "@/shared/lib/ui/scroll-lock";
import { cn } from "@/shared/utils/cn";

export type ConfirmDialogProps = {
  title: string;
  description?: ReactNode;
  confirmLabel: string;
  cancelLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
  /** İşlem sürerken iki düğme de kilitlenir; Esc ve arka plana tıklama kapatmaz. */
  busy?: boolean;
  /** `danger`: geri alınamaz işlemler (silme). İlk odak "Vazgeç"te olur, onay düğmesi kırmızı çerçeveli görünür. */
  tone?: "default" | "danger";
  /** Açıklamanın altında gösterilir (ör. hata bildirimi). */
  children?: ReactNode;
};

/**
 * Projenin ortak onay penceresi ("Emin misiniz?"). Yerel `<dialog>` modalıdır: odak pencerenin içinde kalır, Esc ve arka
 * plana tıklama vazgeçmedir, kapanınca odak önceki öğeye döner. Başka bir modalın içinden de açılabilir (üst katmanda durur).
 * Metinler dışarıdan verilir; bileşen hiçbir çeviri alanına bağlı değildir.
 */
export function ConfirmDialog({ title, description, confirmLabel, cancelLabel, onConfirm, onCancel, busy = false, tone = "default", children }: ConfirmDialogProps) {
  const titleId = useId();
  const descriptionId = useId();
  const ref = useRef<HTMLDialogElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);
  const cancelHandler = useRef(onCancel);
  useEffect(() => { cancelHandler.current = onCancel; });
  useEffect(() => {
    const dialog = ref.current;
    const previous = document.activeElement as HTMLElement | null;
    dialog?.showModal();
    const unlock = lockPageScroll();
    (tone === "danger" ? cancelRef : confirmRef).current?.focus();
    return () => { dialog?.close(); unlock(); previous?.focus(); };
  }, [tone]);
  const danger = tone === "danger";
  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      aria-busy={busy}
      onCancel={(event) => { event.preventDefault(); if (!busy) cancelHandler.current(); }}
      onClick={(event) => { if (event.target === event.currentTarget && !busy) cancelHandler.current(); }}
      className="fixed inset-0 m-auto w-[calc(100%-2rem)] max-w-md overflow-visible rounded-[22px] border border-border bg-surface p-0 text-text-primary shadow-popover backdrop:bg-scrim backdrop:backdrop-blur-[2px]"
    >
      <div className="space-y-5 p-6">
        <div className="flex items-start gap-4">
          <span aria-hidden="true" className={cn("flex size-11 shrink-0 items-center justify-center rounded-full border", danger ? "border-primary/40 bg-primary/10 text-brand-text" : "border-control-border bg-surface-elevated text-text-primary")}>
            <Icon name="info" className="size-5" />
          </span>
          <div className="min-w-0 space-y-1.5">
            <h2 id={titleId} className="font-display text-lg font-semibold leading-6">{title}</h2>
            {description ? <p id={descriptionId} className="text-sm leading-6 text-text-secondary">{description}</p> : null}
          </div>
        </div>
        {children}
        <div className="flex flex-wrap justify-end gap-2">
          <button ref={cancelRef} type="button" disabled={busy} onClick={onCancel} className="inline-flex min-h-11 items-center justify-center rounded-xl border border-control-border bg-surface px-5 text-sm font-medium text-text-primary transition-colors hover:bg-surface-elevated focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus disabled:opacity-60">
            {cancelLabel}
          </button>
          <button ref={confirmRef} type="button" disabled={busy} onClick={onConfirm} className="inline-flex min-h-11 items-center justify-center rounded-xl border border-primary bg-primary px-5 text-sm font-semibold text-primary-foreground transition-colors hover:bg-secondary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus disabled:opacity-60">
            {confirmLabel}
          </button>
        </div>
      </div>
    </dialog>
  );
}
