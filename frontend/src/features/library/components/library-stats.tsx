"use client";

import { useLocale, useTranslations } from "next-intl";
import type { LibraryStatistics } from "../types/library";

export function LibraryStats({ data }: { data: LibraryStatistics }) {
  const t = useTranslations("Library");
  const locale = useLocale();
  return <dl className="grid grid-cols-3 divide-x divide-border border-y border-border py-5">
    {(["totalGames", "favoriteGames", "completedGames"] as const).map((key) => <div key={key} className="min-w-0 px-2 first:ps-0 sm:px-6"><dt className="text-xs text-text-secondary sm:text-sm">{t(`stats.${key}`)}</dt><dd className="mt-2 font-display text-2xl font-semibold tabular-nums sm:text-3xl">{new Intl.NumberFormat(locale).format(data[key])}</dd></div>)}
  </dl>;
}
