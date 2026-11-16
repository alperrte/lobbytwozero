"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";
import { lockPageScroll } from "@/shared/lib/ui/scroll-lock";

export type ModalDialogProps = {
  title: string;
  description?: ReactNode;
  /** Kapatma isteği (Esc, arka plana tıklama, X düğmesi). `dismissible=false` iken yalnız içerikteki kendi düğmen kapatır. */
  onClose: () => void;
  /**
   * `false`: Esc, arka plan ve X kapatmaz; bilgi yalnız bir kez gösterilip onaylanması gereken pencereler içindir
   * (ör. kurtarma kodları: yanlışlıkla kapanırsa bir daha görünmez).
   */
  dismissible?: boolean;
  /** Pencere çerçevesine yerleşen süs katmanı (ör. `CardSnake`). */
  frame?: ReactNode;
  children: ReactNode;
  /** Kapat düğmesinin erişilebilir adı. */
  closeLabel: string;
};

/**
 * Formlu/içerikli modal pencere (`ConfirmDialog`un içerik alan kardeşi). Yerel `<dialog>` modalıdır: odak içeride kalır, kapanınca
 * önceki öğeye döner, sayfa kaydırması kilitlenir. İçerik çağıran tarafındır; pencere hiçbir çeviri alanına bağlı değildir.
 */
export function ModalDialog({ title, description, onClose, dismissible = true, frame, children, closeLabel }: ModalDialogProps) {
  const titleId = useId();
  const descriptionId = useId();
  const ref = useRef<HTMLDialogElement>(null);
  const closeHandler = useRef(onClose);
  useEffect(() => {
    closeHandler.current = onClose;
  });
  useEffect(() => {
    const dialog = ref.current;
    const previous = document.activeElement as HTMLElement | null;
    dialog?.showModal();
    const unlock = lockPageScroll();
    return () => {
      dialog?.close();
      unlock();
      previous?.focus();
    };
  }, []);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      onCancel={(event) => {
        event.preventDefault();
        if (dismissible) closeHandler.current();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget && dismissible) closeHandler.current();
      }}
      className="fixed inset-0 m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-visible rounded-[22px] border border-border bg-surface p-0 text-text-primary shadow-popover backdrop:bg-scrim backdrop:backdrop-blur-[2px]"
    >
      {frame}
      <div className="relative flex max-h-[calc(100dvh-2rem)] flex-col gap-4 overflow-y-auto p-6">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0 space-y-1.5">
            <h2 id={titleId} className="font-display text-lg font-semibold leading-6">
              {title}
            </h2>
            {description ? (
              <p id={descriptionId} className="text-sm leading-6 text-text-secondary">
                {description}
              </p>
            ) : null}
          </div>
          {dismissible ? (
            <button
              type="button"
              aria-label={closeLabel}
              onClick={onClose}
              className="inline-flex size-9 shrink-0 items-center justify-center rounded-full border border-control-border text-text-secondary transition-colors hover:bg-surface-elevated hover:text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
            >
              <span aria-hidden="true">✕</span>
            </button>
          ) : null}
        </div>
        {children}
      </div>
    </dialog>
  );
}
