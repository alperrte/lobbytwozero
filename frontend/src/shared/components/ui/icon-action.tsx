"use client";

import { useEffect, useId, useRef, useState, type ReactNode } from "react";

type IconActionProps = {
  /** Erişilebilir ad, ör. "Alper Temiz — GitHub". */
  label: string;
  /** Üzerine gelince / odaklanınca görünen kısa metin. */
  tooltip: string;
  /** Doluysa dış bağlantı olur; `null` ise simge pasiftir (tıklanamaz, ama odaklanabilir ve açıklanır). */
  href: string | null;
  children: ReactNode;
};

type Position = { left: number; bottom: number };

const TIP_MAX_WIDTH = 240;
const VIEWPORT_GUTTER = 12;

const actionClass =
  "inline-flex size-10 items-center justify-center rounded-control border border-border bg-surface text-text-secondary transition-colors duration-150 motion-reduce:transition-none";
const liveClass = `${actionClass} hover:border-primary/60 hover:bg-surface-elevated hover:text-text-primary`;
const passiveClass = `${actionClass} cursor-not-allowed border-dashed opacity-70`;

/**
 * Kart içi simge bağlantısı: GitHub, LinkedIn ve CV için. Tooltip `position: fixed` ile ekran
 * kenarına taşmadan konumlanır; hover, odak ve dokunmayla açılır, Escape ile kapanır (WCAG 1.4.13).
 * `href` yoksa simge `aria-disabled` bir düğmedir: sahte bağlantı üretilmez.
 */
export function IconAction({ label, tooltip, href, children }: IconActionProps) {
  const tipId = useId();
  const triggerRef = useRef<HTMLAnchorElement & HTMLButtonElement>(null);
  const [position, setPosition] = useState<Position | null>(null);
  const open = position !== null;

  function show() {
    const element = triggerRef.current;
    if (!element) {
      return;
    }
    const rect = element.getBoundingClientRect();
    const width = Math.min(TIP_MAX_WIDTH, window.innerWidth - VIEWPORT_GUTTER * 2);
    const centered = rect.left + rect.width / 2 - width / 2;
    const left = Math.min(
      Math.max(centered, VIEWPORT_GUTTER),
      window.innerWidth - width - VIEWPORT_GUTTER
    );
    setPosition({ left, bottom: window.innerHeight - rect.top + 8 });
  }

  useEffect(() => {
    if (!open) {
      return;
    }
    function close() {
      setPosition(null);
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        close();
      }
    }

    document.addEventListener("keydown", onKeyDown);
    window.addEventListener("scroll", close, true);
    window.addEventListener("resize", close);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("scroll", close, true);
      window.removeEventListener("resize", close);
    };
  }, [open]);

  const shared = {
    "aria-label": label,
    "aria-describedby": open ? tipId : undefined,
    onMouseEnter: show,
    onMouseLeave: () => setPosition(null),
    onFocus: show,
    onBlur: () => setPosition(null)
  };

  return (
    <span className="inline-flex">
      {href ? (
        <a
          ref={triggerRef}
          href={href}
          target="_blank"
          rel="noopener noreferrer"
          className={liveClass}
          {...shared}
        >
          {children}
        </a>
      ) : (
        <button
          ref={triggerRef}
          type="button"
          aria-disabled="true"
          className={passiveClass}
          {...shared}
        >
          {children}
        </button>
      )}
      {open ? (
        <span
          id={tipId}
          role="tooltip"
          style={{ left: position.left, bottom: position.bottom, maxWidth: TIP_MAX_WIDTH }}
          className="pointer-events-none fixed z-50 w-max rounded-control border border-border bg-surface-elevated px-3 py-1.5 text-sm text-text-primary shadow-popover animate-pop-in"
        >
          {tooltip}
        </span>
      ) : null}
    </span>
  );
}
