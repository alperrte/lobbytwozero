"use client";

import { useTranslations } from "next-intl";
import { Icon, type IconName } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import type { FeedbackEntry } from "../../hooks/use-item-feedback";
import type { FeedbackValue } from "../../types/recommendation";

const options: { value: FeedbackValue; icon: IconName }[] = [
  { value: "LIKE", icon: "check" },
  { value: "NOT_INTERESTED", icon: "eyeOff" }
];

/**
 * Beğen / İlgilenmiyorum. Seçili değer `aria-pressed` ile duyurulur; istek sürerken iki düğme de pasiftir
 * (çift gönderim yok). Backend geri bildirimi silmeyi desteklemediğinden seçili düğmeye tekrar basmak bir şey yapmaz.
 */
export function FeedbackActions({ gameTitle, entry, pending, onSelect }: { gameTitle: string; entry: FeedbackEntry; pending: boolean; onSelect: (value: FeedbackValue) => void }) {
  const t = useTranslations("Recommendation.feedback");
  return <div role="group" aria-label={t("groupLabel", { game: gameTitle })} aria-busy={pending} className="grid grid-cols-2 gap-2">
    {options.map((option) => {
      const selected = entry.value === option.value;
      return <button
        key={option.value}
        type="button"
        aria-pressed={selected}
        disabled={pending}
        onClick={() => onSelect(option.value)}
        className={cn(
          "inline-flex h-9 min-w-0 items-center justify-center gap-1.5 rounded-control border px-2 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-60 motion-reduce:transition-none",
          selected ? "border-primary bg-primary/12 text-text-primary" : "border-border bg-surface-elevated text-text-secondary hover:border-primary/60 hover:text-text-primary"
        )}
      >
        <Icon name={option.icon} className="size-4 shrink-0" />
        <span className="truncate">{t(option.value)}</span>
      </button>;
    })}
  </div>;
}
