"use client";

import { useTranslations } from "next-intl";
import {
  useResolvedTheme,
  useThemePreference
} from "@/shared/hooks/use-theme-preference";
import type { ResolvedTheme } from "@/shared/lib/preferences/preferences";
import { Icon } from "./icons";

/*
 * Güneş/ay anahtarı. Aktif durumun görseli <html data-theme> üzerinden CSS ile verilir; böylece
 * kayıtlı tema ilk boyamada doğru görünür (hydration sonrası sıçrama olmaz). aria-pressed,
 * hydration sonrası gerçek duruma eşitlenir. "Sistem" tercihi Ayarlar panelindedir; burada
 * görünen tema değiştirilir.
 */
const options: ReadonlyArray<{
  value: ResolvedTheme;
  icon: "sun" | "moon";
  active: string;
}> = [
  {
    value: "light",
    icon: "sun",
    active:
      "in-data-[theme=light]:bg-sun in-data-[theme=light]:text-on-celestial"
  },
  {
    value: "dark",
    icon: "moon",
    active:
      "in-data-[theme=dark]:bg-moon in-data-[theme=dark]:text-on-celestial"
  }
];

export function ThemeSwitch() {
  const t = useTranslations("Theme");
  const [preference, setPreference] = useThemePreference();
  const resolved = useResolvedTheme();
  const current = preference === "system" ? resolved : preference;

  return (
    <div
      role="group"
      aria-label={t("label")}
      className="inline-flex shrink-0 gap-0.5 rounded-control border border-border bg-surface-elevated p-0.5"
    >
      {options.map(({ value, icon, active }) => (
        <button
          key={value}
          type="button"
          aria-pressed={current === value}
          aria-label={t(value)}
          title={t(value)}
          onClick={() => setPreference(value)}
          className={`inline-flex size-8 items-center justify-center rounded-control text-text-secondary transition-colors not-aria-pressed:hover:text-text-primary ${active}`}
        >
          <Icon name={icon} className="size-[18px]" />
        </button>
      ))}
    </div>
  );
}
