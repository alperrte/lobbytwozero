"use client";

import { useRef, type KeyboardEvent } from "react";
import { RATING_MAX, RATING_MIN } from "../schemas/review-schema";
import { cn } from "@/shared/utils/cn";
import { focusRing } from "./review-ui";

const values = Array.from({ length: RATING_MAX - RATING_MIN + 1 }, (_, index) => RATING_MIN + index);

/**
 * 1–10 tam sayı puan seçici (WCAG: radio group). Tek sekme durağı, ok tuşlarıyla seçim, Home/End; seçili değer renkle
 * birlikte `aria-checked` ve görünür rakamla belirtilir. Her düğme dokunmatik için en az 44 px'tir. `value === null`: puan yok.
 */
export function RatingPicker({ value, onChange, disabled = false, label, describedBy, optionLabel }: { value: number | null; onChange: (value: number) => void; disabled?: boolean; label: string; describedBy?: string; optionLabel: (value: number) => string }) {
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);
  const tabStop = value ?? RATING_MIN;

  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    let next = index;
    if (event.key === "ArrowRight" || event.key === "ArrowDown") next = Math.min(values.length - 1, index + 1);
    else if (event.key === "ArrowLeft" || event.key === "ArrowUp") next = Math.max(0, index - 1);
    else if (event.key === "Home") next = 0;
    else if (event.key === "End") next = values.length - 1;
    else return;
    event.preventDefault();
    onChange(values[next]);
    buttons.current[next]?.focus();
  }

  return (
    <div role="radiogroup" aria-label={label} aria-describedby={describedBy} aria-disabled={disabled || undefined} className="grid grid-cols-5 gap-2 sm:grid-cols-10">
      {values.map((rating, index) => {
        const checked = value === rating;
        return (
          <button
            key={rating}
            ref={(element) => {
              buttons.current[index] = element;
            }}
            type="button"
            role="radio"
            aria-checked={checked}
            aria-label={optionLabel(rating)}
            tabIndex={rating === tabStop ? 0 : -1}
            disabled={disabled}
            onClick={() => onChange(rating)}
            onKeyDown={(event) => onKeyDown(event, index)}
            className={cn(
              "inline-flex min-h-11 min-w-11 items-center justify-center rounded-control border font-display text-base font-semibold tabular-nums transition-colors motion-reduce:transition-none disabled:cursor-not-allowed disabled:opacity-55",
              focusRing,
              checked ? "border-primary bg-primary text-primary-foreground" : "border-control-border bg-surface-elevated text-text-primary hover:border-primary/60"
            )}
          >
            {rating}
          </button>
        );
      })}
    </div>
  );
}
