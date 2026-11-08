"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";
import { Icon } from "./icons";
import { buttonClass } from "./button-styles";

type ConfirmDialogProps = {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  cancelLabel: string;
  closeLabel: string;
  /** İşlem sürerken düğme metni (ör. "Kaldırılıyor…"); verilmezse `confirmLabel` kalır. */
  pendingLabel?: string;
  pending?: boolean;
  /** İşlem başarısız olursa pencerede kalır ve bu metni duyurur. */
  error?: string | null;
  onConfirm: () => void;
  onClose: () => void;
  /** Başlıkla açıklama arasında gösterilen özet (ör. kullanıcı satırı). */
  children?: ReactNode;
};

/**
 * Açık onay penceresi (web-rules §4 Confirmation). Yerel `<dialog>` + `showModal()`: odak tuzağı, üst katman ve `Esc` tarayıcıdan
 * gelir. Açılınca odak güvenli seçenekte ("Vazgeç") olur; kapanınca tetikleyiciye döner. İşlem sürerken kapatılamaz.
 */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  cancelLabel,
  closeLabel,
  pendingLabel,
  pending = false,
  error,
  onConfirm,
  onClose,
  children
}: ConfirmDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);
  const titleId = useId();
  const descriptionId = useId();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) {
      return;
    }
    if (open && !dialog.open) {
      dialog.showModal();
      cancelRef.current?.focus();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  function requestClose() {
    if (!pending) {
      onClose();
    }
  }

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      onCancel={(event) => {
        event.preventDefault();
        requestClose();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          requestClose();
        }
      }}
      className="m-auto w-[min(28rem,calc(100vw-2rem))] rounded-panel border border-border bg-surface p-0 text-text-primary shadow-popover backdrop:bg-scrim open:animate-pop-in"
    >
      <div className="p-5 sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <h2 id={titleId} className="font-display text-xl font-semibold">
            {title}
          </h2>
          <button
            type="button"
            onClick={requestClose}
            disabled={pending}
            aria-label={closeLabel}
            className="-me-2 -mt-1 inline-flex size-9 shrink-0 items-center justify-center rounded-control text-text-secondary transition-colors hover:bg-surface-elevated hover:text-text-primary disabled:opacity-50"
          >
            <Icon name="close" />
          </button>
        </div>
        <div className="mt-1 h-0.5 w-16 bg-gradient-to-r from-primary to-accent" aria-hidden="true" />
        {children ? <div className="mt-5">{children}</div> : null}
        <p id={descriptionId} className="mt-4 text-sm leading-relaxed text-text-secondary">
          {description}
        </p>
        {error ? (
          <p role="alert" className="mt-4 flex items-start gap-2 text-sm text-brand-text">
            <Icon name="info" className="mt-0.5 size-4 shrink-0" />
            {error}
          </p>
        ) : null}
        <div className="mt-6 grid grid-cols-2 gap-3">
          <button
            ref={cancelRef}
            type="button"
            onClick={requestClose}
            disabled={pending}
            className={buttonClass("secondary")}
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={pending}
            aria-busy={pending || undefined}
            className={buttonClass("primary")}
          >
            {pending && pendingLabel ? pendingLabel : confirmLabel}
          </button>
        </div>
      </div>
    </dialog>
  );
}
