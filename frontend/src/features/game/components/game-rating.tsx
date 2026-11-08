"use client";

import { useLocale, useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import type { GameRating } from "../types/game";

/** UNRATED (henüz puan yok) ile UNAVAILABLE (veri alınamadı) farklıdır; hiçbiri 0 puan olarak gösterilmez. */
export function GameRatingText({ rating, compact = false }: { rating?: GameRating; compact?: boolean }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  if (rating?.status === "RATED" && rating.averageRating !== null) {
    const value = new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(rating.averageRating);
    return <span className="inline-flex items-center gap-1.5 text-sm"><Icon name="star" width={16} height={16} className="text-warning" />
      <strong className="tabular-nums">{value}</strong><span className="text-text-secondary">{compact ? "/10" : `/10 · ${t("ratingCount", { count: rating.ratingCount ?? 0 })}`}</span></span>;
  }
  return <span className="text-sm text-text-secondary">{t(rating?.status === "UNRATED" ? "unrated" : "ratingUnavailable")}</span>;
}

export function formatReleaseDate(value: string | null | undefined, locale: string): string | null {
  if (!value) return null;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isNaN(date.getTime()) ? null : new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" }).format(date);
}
