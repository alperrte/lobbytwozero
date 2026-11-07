"use client";

import { useFormatter, useTranslations } from "next-intl";
import type { CountSection, ProfileStats } from "../types/profile.types";

const keys = ["friends", "library", "reviews"] as const;

/**
 * İstatistik şeridi. Sayılar kaynak servisten geldiği için yalnız `AVAILABLE` iken gösterilir; `HIDDEN` "gizli",
 * `UNAVAILABLE` "henüz yok" der ve sayı uydurulmaz. `stats === null`: özet alınamadı. Mesaj sayısının backend karşılığı
 * yoktur, "henüz yok" olarak kalır.
 */
export function ProfileStatsStrip({ stats }: { stats: ProfileStats | null }) {
  const t = useTranslations("Profile.stats");
  const format = useFormatter();

  function value(section: CountSection | null): string {
    if (section === null) return t("unknown");
    if (section.status === "AVAILABLE" && section.count !== null) return format.number(section.count);
    return section.status === "HIDDEN" ? t("hidden") : t("unavailable");
  }

  return (
    <section aria-label={t("label")}>
      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {keys.map((key) => (
          <Stat key={key} label={t(key)} value={value(stats?.[key] ?? null)} />
        ))}
        <Stat label={t("messages")} value={t("unavailable")} />
      </dl>
    </section>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-panel border border-border bg-surface px-4 py-3">
      <dt className="text-xs text-text-secondary">{label}</dt>
      <dd className="mt-1 font-display text-lg font-semibold text-text-primary">{value}</dd>
    </div>
  );
}
