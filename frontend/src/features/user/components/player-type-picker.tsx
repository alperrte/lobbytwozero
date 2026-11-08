"use client";

import { useTranslations } from "next-intl";
import { cn } from "@/shared/utils/cn";
import { playerTypes, type PlayerType } from "../types/profile.types";

const MAX_TYPES = 2;

/**
 * Oyuncu tipi seçimi: her tip kısa bir açıklamayla kart olarak gelir (`aria-pressed`). Sunucu en çok 2 tip kabul eder;
 * sınıra gelince seçilmemişler devre dışı kalır.
 */
export function PlayerTypePicker({ value, onChange }: { value: PlayerType[]; onChange: (value: PlayerType[]) => void }) {
  const t = useTranslations("Profile");
  const full = value.length >= MAX_TYPES;

  function toggle(type: PlayerType) {
    if (value.includes(type)) {
      onChange(value.filter((item) => item !== type));
    } else if (!full) {
      onChange([...value, type]);
    }
  }

  return (
    <fieldset className="flex flex-col gap-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <legend className="text-sm font-medium">{t("playerTypes")}</legend>
        <span className={cn("text-xs", full ? "text-brand-text" : "text-text-secondary")} aria-live="polite">
          {t("picker.count", { count: value.length, max: MAX_TYPES })}
        </span>
      </div>
      <p className="text-xs text-text-secondary">{t("playerTypesHint")}</p>
      <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {playerTypes.map((type) => {
          const on = value.includes(type);
          return (
            <li key={type}>
              <button
                type="button"
                aria-pressed={on}
                disabled={!on && full}
                onClick={() => toggle(type)}
                className={cn(
                  "flex h-full w-full flex-col gap-1 rounded-panel border p-3 text-start transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-50",
                  on
                    ? "border-primary bg-primary/15"
                    : "border-border bg-surface-elevated hover:border-control-border"
                )}
              >
                <span className={cn("font-display text-sm font-semibold", on && "text-brand-text")}>
                  {t(`playerType.${type}`)}
                </span>
                <span className="text-xs text-text-secondary">{t(`playerTypeHint.${type}`)}</span>
              </button>
            </li>
          );
        })}
      </ul>
    </fieldset>
  );
}
