"use client";

import { useRef, type KeyboardEvent } from "react";
import { cn } from "@/shared/utils/cn";
import { focusRing } from "./review-ui";

type Choice = { value: boolean; label: string };

/**
 * Olumlu/olumsuz tavsiye seçici (radio group). `value === null`: henüz tavsiye yok — bu `false` DEĞİLDİR ve hiçbir seçenek
 * işaretli görünmez. Puandan bağımsızdır.
 */
export function RecommendationPicker({ value, onChange, disabled = false, label, positiveLabel, negativeLabel }: { value: boolean | null; onChange: (value: boolean) => void; disabled?: boolean; label: string; positiveLabel: string; negativeLabel: string }) {
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);
  const choices: Choice[] = [
    { value: true, label: positiveLabel },
    { value: false, label: negativeLabel }
  ];
  const tabStop = value === false ? 1 : 0;

  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    let next = index;
    if (event.key === "ArrowRight" || event.key === "ArrowDown") next = Math.min(1, index + 1);
    else if (event.key === "ArrowLeft" || event.key === "ArrowUp") next = Math.max(0, index - 1);
    else return;
    event.preventDefault();
    onChange(choices[next].value);
    buttons.current[next]?.focus();
  }

  return (
    <div role="radiogroup" aria-label={label} aria-disabled={disabled || undefined} className="grid gap-2 sm:grid-cols-2">
      {choices.map((choice, index) => {
        const checked = value === choice.value;
        return (
          <button
            key={String(choice.value)}
            ref={(element) => {
              buttons.current[index] = element;
            }}
            type="button"
            role="radio"
            aria-checked={checked}
            tabIndex={index === tabStop ? 0 : -1}
            disabled={disabled}
            onClick={() => onChange(choice.value)}
            onKeyDown={(event) => onKeyDown(event, index)}
            className={cn(
              "inline-flex min-h-11 items-center justify-center gap-2 rounded-control border px-4 text-sm font-semibold transition-colors motion-reduce:transition-none disabled:cursor-not-allowed disabled:opacity-55",
              focusRing,
              checked ? "border-primary bg-primary text-primary-foreground" : "border-control-border bg-surface-elevated text-text-primary hover:border-primary/60"
            )}
          >
            <span aria-hidden="true">{checked ? "●" : "○"}</span>
            {choice.label}
          </button>
        );
      })}
    </div>
  );
}
