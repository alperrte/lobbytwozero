"use client";

import { useEffect, useId, useRef, useState } from "react";
import { useTranslations } from "next-intl";

type InfoTipProps = {
  /** Neyin açıklandığı (ekran okuyucu adı için), ör. "Tema". */
  subject: string;
  /** Ayarın ne işe yaradığını anlatan kısa metin. */
  children: string;
};

type Position = { left: number; top: number; width: number };

const TIP_MAX_WIDTH = 288;
const VIEWPORT_GUTTER = 12;

/**
 * Yuvarlak içinde "i" simgesi: üzerine gelince, odaklanınca ya da dokununca kısa açıklama gösterir.
 * Açıklama `position: fixed` ile konumlanır ve ekran kenarına taşmaz; Escape, dışarı tıklama,
 * kaydırma ya da odak kaybıyla kapanır. Metin `aria-describedby` ile düğmeye bağlanır.
 */
export function InfoTip({ subject, children }: InfoTipProps) {
  const t = useTranslations("Settings");
  const tipId = useId();
  const buttonRef = useRef<HTMLButtonElement>(null);
  const [position, setPosition] = useState<Position | null>(null);
  const open = position !== null;

  function show() {
    const button = buttonRef.current;
    if (!button) {
      return;
    }
    const rect = button.getBoundingClientRect();
    const width = Math.min(TIP_MAX_WIDTH, window.innerWidth - VIEWPORT_GUTTER * 2);
    const centered = rect.left + rect.width / 2 - width / 2;
    const left = Math.min(
      Math.max(centered, VIEWPORT_GUTTER),
      window.innerWidth - width - VIEWPORT_GUTTER
    );
    setPosition({ left, top: rect.bottom + 8, width });
  }

  function hide() {
    setPosition(null);
  }

  useEffect(() => {
    if (!open) {
      return;
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setPosition(null);
      }
    }
    function onPointerDown(event: PointerEvent) {
      if (!buttonRef.current?.contains(event.target as Node)) {
        setPosition(null);
      }
    }
    function onScroll() {
      setPosition(null);
    }

    document.addEventListener("keydown", onKeyDown);
    document.addEventListener("pointerdown", onPointerDown);
    window.addEventListener("scroll", onScroll, true);
    window.addEventListener("resize", onScroll);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.removeEventListener("pointerdown", onPointerDown);
      window.removeEventListener("scroll", onScroll, true);
      window.removeEventListener("resize", onScroll);
    };
  }, [open]);

  return (
    <span className="inline-flex">
      <button
        ref={buttonRef}
        type="button"
        aria-label={t("infoLabel", { subject })}
        aria-describedby={open ? tipId : undefined}
        onMouseEnter={show}
        onMouseLeave={hide}
        onFocus={show}
        onBlur={hide}
        onClick={show}
        className="inline-flex size-6 shrink-0 items-center justify-center rounded-full border border-primary/40 text-text-secondary transition-colors hover:border-primary hover:text-text-primary aria-[describedby]:border-primary aria-[describedby]:text-text-primary"
      >
        <svg
          viewBox="0 0 24 24"
          className="size-3.5"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.25"
          strokeLinecap="round"
          aria-hidden="true"
          focusable="false"
        >
          <path d="M12 11v6M12 7v.01" />
        </svg>
      </button>
      {open ? (
        <span
          id={tipId}
          role="tooltip"
          style={{ left: position.left, top: position.top, width: position.width }}
          className="pointer-events-none fixed z-50 rounded-panel border border-border bg-surface-elevated px-3 py-2 text-sm text-text-primary shadow-popover animate-pop-in"
        >
          {children}
        </span>
      ) : null}
    </span>
  );
}
