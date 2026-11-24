"use client";

import { useEffect, useId, useRef } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { lockPageScroll } from "@/shared/lib/ui/scroll-lock";
import { GuideFlow } from "./guide-flow";

/**
 * LTZ Rehber yan paneli. Yerel `<dialog>` modalıdır: odak panelde kalır, Esc ve arka plan kapatır, kapanınca odak tetikleyiciye
 * döner. Mobilde tam genişlik, geniş ekranda sağda sabit genişlikli paneldir.
 */
export function LtzGuideDrawer({ onClose }: { onClose: () => void }) {
  const t = useTranslations("Recommendation.guide");
  const common = useTranslations("Recommendation.common");
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

  return <dialog
    ref={ref}
    aria-labelledby={titleId}
    aria-describedby={descriptionId}
    onCancel={(event) => { event.preventDefault(); closeHandler.current(); }}
    onClick={(event) => { if (event.target === event.currentTarget) closeHandler.current(); }}
    className="fixed inset-y-0 end-0 start-auto m-0 h-dvh max-h-dvh w-full max-w-md border-s border-border bg-surface p-0 text-text-primary shadow-popover backdrop:bg-scrim backdrop:backdrop-blur-[2px]"
  >
    <div className="flex h-full flex-col">
      <header className="flex items-start justify-between gap-4 border-b border-border px-5 py-4">
        <div className="min-w-0">
          <h2 id={titleId} className="flex items-center gap-2 font-display text-lg font-semibold">
            <Icon name="message" className="size-5 shrink-0 text-brand-text" />
            {t("title")}
          </h2>
          <p id={descriptionId} className="mt-1 text-sm leading-6 text-text-secondary">{t("description")}</p>
        </div>
        <button type="button" aria-label={common("close")} onClick={onClose} className="inline-flex size-9 shrink-0 items-center justify-center rounded-full border border-control-border text-text-secondary transition-colors hover:bg-surface-elevated hover:text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus">
          <Icon name="close" className="size-4" />
        </button>
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 py-5">
        <GuideFlow onNavigate={onClose} />
      </div>
    </div>
  </dialog>;
}
