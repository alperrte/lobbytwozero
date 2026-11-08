"use client";

import { useRef, useState, type CSSProperties, type KeyboardEvent, type PointerEvent } from "react";
import { cn } from "@/shared/utils/cn";

const STAR_PATH = "M12 2.6l2.9 5.87 6.48.94-4.69 4.57 1.1 6.45L12 17.38l-5.79 3.05 1.1-6.45L2.62 9.41l6.48-.94L12 2.6z";

export type StarRatingProps = {
  /** Seçili puan; `null` puan verilmedi demektir. */
  value: number | null;
  /** Verilmezse bileşen yalnız gösterim amaçlıdır (etkileşimsiz). */
  onChange?: (value: number | null) => void;
  /** `true` ise 0,5 adımlarla yarım yıldız verilebilir. */
  allowHalf?: boolean;
  max?: number;
  disabled?: boolean;
  /** Erişilebilir ad (ör. "Puan"). */
  label: string;
  /** Puanı metne çevirir (ör. "4,5 / 5"); sağdaki canlı metin ve ekran okuyucu değeri için kullanılır. */
  formatValue: (value: number) => string;
  /** Puan yokken gösterilen metin (ör. "Puan verilmedi"). */
  emptyText: string;
  /** Verilirse seçili puan varken puanı kaldıran düğme gösterilir. */
  clearLabel?: string;
  /** Yalnız gösterimde sağdaki metni de yazdırır. */
  showText?: boolean;
  size?: "sm" | "lg";
  className?: string;
};

const cell = { sm: "size-5 p-px", lg: "size-8 p-[3px]" } as const;

function Star({ fill, previewing }: { fill: number; previewing: boolean }) {
  return (
    <span className={cn("relative block size-full transition-transform duration-150 motion-reduce:transition-none", previewing && fill > 0 && "scale-110")}>
      <svg viewBox="0 0 24 24" aria-hidden="true" className="absolute inset-0 size-full text-control-border" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round">
        <path d={STAR_PATH} />
      </svg>
      <span className="absolute inset-y-0 start-0 overflow-hidden" style={{ width: `${Math.round(fill * 100)}%` }}>
        <svg viewBox="0 0 24 24" aria-hidden="true" className={cn("size-full max-w-none transition-colors duration-150", previewing ? "text-brand-text" : "text-warning")} style={{ width: "var(--star-size)", height: "var(--star-size)" }} fill="currentColor" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round">
          <path d={STAR_PATH} />
        </svg>
      </span>
    </span>
  );
}

/**
 * LTZ yıldız puanı (1–5, isteğe bağlı yarım yıldız). Fare/dokunma ile üzerine gelince veya sürüklerken vereceğin puan
 * önizlenir, sağdaki metin anlık değeri yazar; tıklayınca ya da bırakınca seçilir. Klavyede ok tuşları (Home: kaldır,
 * End: en yüksek) ve `role="slider"` ile erişilebilir. `onChange` verilmezse salt okunur gösterimdir.
 */
export function StarRating({ value, onChange, allowHalf = false, max = 5, disabled = false, label, formatValue, emptyText, clearLabel, showText = false, size = "lg", className }: StarRatingProps) {
  const step = allowHalf ? 0.5 : 1;
  const trackRef = useRef<HTMLDivElement>(null);
  const dragging = useRef(false);
  const [hover, setHover] = useState<number | null>(null);
  const interactive = Boolean(onChange) && !disabled;
  const shown = hover ?? value ?? 0;
  const previewing = hover !== null && hover !== value;
  // Hücre içi (dolgu düşülmüş) yıldız boyutu: lg 32-6, sm 20-2.
  const px = size === "lg" ? 26 : 18;
  const text = hover !== null ? formatValue(hover) : value !== null ? formatValue(value) : emptyText;

  function valueAt(clientX: number): number | null {
    const rect = trackRef.current?.getBoundingClientRect();
    if (!rect || rect.width <= 0) return null;
    const ratio = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
    return Math.min(max, Math.max(step, Math.ceil((ratio * max) / step) * step));
  }
  function onPointerDown(event: PointerEvent<HTMLDivElement>) {
    if (!interactive) return;
    dragging.current = true;
    event.currentTarget.setPointerCapture?.(event.pointerId);
    setHover(valueAt(event.clientX));
  }
  function onPointerMove(event: PointerEvent<HTMLDivElement>) {
    if (!interactive) return;
    setHover(valueAt(event.clientX));
  }
  function onPointerUp(event: PointerEvent<HTMLDivElement>) {
    if (!interactive || !dragging.current) return;
    dragging.current = false;
    event.currentTarget.releasePointerCapture?.(event.pointerId);
    const next = valueAt(event.clientX);
    if (next !== null) onChange?.(next);
    setHover(null);
  }
  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (!interactive) return;
    const current = value ?? 0;
    let next: number | null | undefined;
    if (event.key === "ArrowRight" || event.key === "ArrowUp") next = Math.min(max, current + step);
    else if (event.key === "ArrowLeft" || event.key === "ArrowDown") next = current - step < step ? null : current - step;
    else if (event.key === "Home") next = null;
    else if (event.key === "End") next = max;
    if (next === undefined) return;
    event.preventDefault();
    onChange?.(next);
  }

  const stars = Array.from({ length: max }, (_, index) => (
    <span key={index} className={cell[size]}>
      <Star fill={Math.min(1, Math.max(0, shown - index))} previewing={previewing} />
    </span>
  ));

  if (!onChange) {
    return (
      <span className={cn("inline-flex items-center gap-2", className)}>
        <span role="img" aria-label={value === null ? emptyText : formatValue(value)} className="inline-flex" style={{ "--star-size": `${px}px` } as CSSProperties}>{stars}</span>
        {showText ? <span aria-hidden="true" className="text-sm text-text-secondary">{value === null ? emptyText : formatValue(value)}</span> : null}
      </span>
    );
  }

  return (
    <div className={cn("flex flex-wrap items-center gap-x-4 gap-y-2", className)}>
      <div
        ref={trackRef}
        role="slider"
        tabIndex={disabled ? -1 : 0}
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={max}
        aria-valuenow={value ?? 0}
        aria-valuetext={value === null ? emptyText : formatValue(value)}
        aria-disabled={disabled || undefined}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={() => { dragging.current = false; setHover(null); }}
        onPointerLeave={() => { if (!dragging.current) setHover(null); }}
        onKeyDown={onKeyDown}
        style={{ "--star-size": `${px}px` } as CSSProperties}
        className={cn("inline-flex select-none rounded-xl p-1 outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus", interactive ? "cursor-pointer touch-none" : "cursor-not-allowed opacity-60")}
      >
        {stars}
      </div>
      <span data-testid="star-rating-text" className={cn("min-w-28 text-sm font-semibold tabular-nums transition-colors duration-150", previewing ? "text-brand-text" : value === null && hover === null ? "font-normal text-text-secondary" : "text-text-primary")}>
        {text}
      </span>
      {clearLabel && value !== null && interactive ? (
        <button type="button" className="rounded-control text-xs font-medium text-text-secondary underline underline-offset-2 hover:text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus" onClick={() => { onChange?.(null); setHover(null); }}>
          {clearLabel}
        </button>
      ) : null}
    </div>
  );
}
