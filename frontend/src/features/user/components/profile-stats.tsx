"use client";

import { useFormatter, useTranslations } from "next-intl";
import type { Remote } from "../hooks/use-remote";
import type { FriendsPage, HardwareSummary, LibraryStats } from "../types/activity.types";
import type { CountSection, ProfileStats } from "../types/profile.types";

/**
 * İstatistik şeridi. Arkadaş, oyun ve bilgisayar sayıları ilgili servislerden doğrudan okunur (Social, Library, Hardware);
 * inceleme sayısı için henüz doğrudan uç yoktur ve User'ın özetinden gelir. Sayı yalnız kaynak verdiğinde yazılır:
 * yüklenirken "…", alınamazsa "Alınamadı", özette `HIDDEN` "gizli", `UNAVAILABLE` "henüz yok" der; sayı uydurulmaz.
 * Arkadaş sayısı sunucudan toplam olarak gelmez: ilk sayfadan fazlası varsa "N+" yazılır.
 */
export function ProfileStatsStrip({
  friends,
  library,
  hardware,
  stats
}: {
  friends: Remote<FriendsPage>;
  library: Remote<LibraryStats>;
  hardware: Remote<HardwareSummary>;
  stats: ProfileStats | null;
}) {
  const t = useTranslations("Profile.stats");
  const format = useFormatter();

  function fromRemote<T>(remote: Remote<T>, pick: (data: T) => string): string {
    const { state } = remote;
    if (state.status === "ready") return pick(state.data);
    return state.status === "error" ? t("unknown") : "…";
  }

  function fromSummary(section: CountSection | null): string {
    if (section === null) return t("unknown");
    if (section.status === "AVAILABLE" && section.count !== null) return format.number(section.count);
    return section.status === "HIDDEN" ? t("hidden") : t("unavailable");
  }

  return (
    <section aria-label={t("label")}>
      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Stat
          label={t("friends")}
          value={fromRemote(friends, (page) =>
            page.hasMore ? t("plus", { count: format.number(page.items.length) }) : format.number(page.items.length)
          )}
        />
        <Stat label={t("library")} value={fromRemote(library, (data) => format.number(data.total))} />
        <Stat label={t("computers")} value={fromRemote(hardware, (data) => format.number(data.computers))} />
        <Stat label={t("reviews")} value={fromSummary(stats?.reviews ?? null)} />
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
