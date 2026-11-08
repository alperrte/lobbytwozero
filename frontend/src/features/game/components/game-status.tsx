"use client";

import { useTranslations } from "next-intl";
import { ApiHttpError } from "@/shared/lib/api/client";

export const controlClass = "min-h-11 rounded-control border border-control-border bg-surface px-4 py-2 text-sm text-text-primary hover:bg-surface-elevated disabled:cursor-not-allowed disabled:opacity-50";


export function GameStatus({ error, loading, onRetry }: { error?: unknown; loading: boolean; onRetry: () => void }) {
  const t = useTranslations("Game");
  if (loading) return <p role="status" className="py-8 text-text-secondary">{t("loading")}</p>;
  if (!error) return null;
  const status = error instanceof ApiHttpError ? String(error.status) : "network";
  const known = ["400", "401", "403", "404", "409", "429", "500", "502", "503", "504"].includes(status);
  return <div role="alert" className="space-y-4 py-6">
    <p>{error instanceof ApiHttpError && error.problem?.code === "RATING_INTEGRATION_PENDING" ? t("ratingPending") : t(`errors.${known ? status : "network"}`)}</p>
    <button type="button" className={controlClass} onClick={onRetry}>{t("retry")}</button>
  </div>;
}
