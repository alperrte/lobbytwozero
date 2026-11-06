"use client";

import { useTranslations } from "next-intl";
import { useThemePreference } from "@/shared/hooks/use-theme-preference";
import type { ThemePreference } from "@/shared/lib/preferences/preferences";
import { Icon, type IconName } from "./icons";

/*
 * Seçili durumun görsel işareti <html data-theme-pref> üzerinden CSS ile verilir;
 * böylece kayıtlı tercih ilk boyamada doğru görünür (hydration sonrası sıçrama olmaz).
 * aria-pressed hydration sonrası gerçek tercihle eşitlenir.
 */
const options: ReadonlyArray<{
  value: ThemePreference;
  icon: IconName;
  selected: string;
}> = [
  {
    value: "system",
    icon: "contrast",
    selected:
      "in-data-[theme-pref=system]:bg-surface in-data-[theme-pref=system]:text-text-primary in-data-[theme-pref=system]:shadow-[inset_0_-2px_0_0_var(--ltz-primary)]"
  },
  {
    value: "light",
    icon: "sun",
    selected:
      "in-data-[theme-pref=light]:bg-surface in-data-[theme-pref=light]:text-text-primary in-data-[theme-pref=light]:shadow-[inset_0_-2px_0_0_var(--ltz-primary)]"
  },
  {
    value: "dark",
    icon: "moon",
    selected:
      "in-data-[theme-pref=dark]:bg-surface in-data-[theme-pref=dark]:text-text-primary in-data-[theme-pref=dark]:shadow-[inset_0_-2px_0_0_var(--ltz-primary)]"
  }
];

export function ThemeToggle() {
  const t = useTranslations("Theme");
  const [preference, setPreference] = useThemePreference();

  return (
    <div
      role="group"
      aria-label={t("label")}
      className="inline-flex rounded-control border border-border bg-surface-elevated p-0.5"
    >
      {options.map(({ value, icon, selected }) => (
        <button
          key={value}
          type="button"
          aria-pressed={preference === value}
          aria-label={t(value)}
          title={t(value)}
          onClick={() => setPreference(value)}
          className={`inline-flex h-9 w-9 items-center justify-center rounded-control text-text-secondary transition-colors hover:text-text-primary ${selected}`}
        >
          <Icon name={icon} className="size-[18px]" />
        </button>
      ))}
    </div>
  );
}
