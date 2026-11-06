"use client";

import { useEffect, useRef, type ReactNode } from "react";
import { cn } from "@/shared/utils/cn";

type ModalDialogProps = {
  open: boolean;
  onClose: () => void;
  /** Erişilebilir ad: görünür başlığın id'si. */
  labelledBy: string;
  className?: string;
  children: ReactNode;
};

/**
 * Yerel <dialog> tabanlı modal: odak tuzağı, Escape ve arka plan yalıtımı tarayıcıdan gelir.
 * Kapanınca odak, açan öğeye otomatik döner. İçerik yalnız açıkken bağlanır; böylece kapalı
 * panel DOM'da boşuna durmaz. Arka plana (backdrop) tıklamak kapatır.
 */
export function ModalDialog({
  open,
  onClose,
  labelledBy,
  className,
  children
}: ModalDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) {
      return;
    }
    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  // Modal açıkken sayfa kaymasını kilitle (kaydırma çubuğu payı korunur).
  useEffect(() => {
    if (!open) {
      return;
    }
    const root = document.documentElement;
    root.style.overflow = "hidden";
    return () => {
      root.style.overflow = "";
    };
  }, [open]);

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={labelledBy}
      onClose={onClose}
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
      className={cn(
        "m-0 border-0 bg-transparent p-0 text-text-primary backdrop:bg-scrim",
        className
      )}
    >
      {open ? children : null}
    </dialog>
  );
}
