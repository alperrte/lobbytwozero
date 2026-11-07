"use client";

import { useId, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import type { CatalogStatus } from "../types/game";
import { controlClass, GameStatus } from "./game-status";
import { Badge } from "./game-ui";

function StatusBody() {
  const t = useTranslations("Game");
  const locale = useLocale();
  const result = useGameResource<CatalogStatus>({ kind: "catalogStatus" }, { refreshOnFocus: false });
  const number = new Intl.NumberFormat(locale);
  const date = (value: string | null) => value ? new Date(value).toLocaleString(locale) : t("notRecorded");
  const status = result.data;
  const tone = status?.syncStatus === "COMPLETE" ? "success" : status?.syncStatus === "FAILED" ? "warning" : "neutral";
  const rows: [string, string][] = status ? [
    ["provider", status.provider],
    ["runMode", status.runMode ? (t.has(`runModes.${status.runMode}`) ? t(`runModes.${status.runMode}`) : status.runMode) : t("notRecorded")],
    ["cursor", status.cursor == null ? t("notRecorded") : String(status.cursor)],
    ["processedCount", number.format(status.processedCount)],
    ["indexedEntries", number.format(status.indexedEntries)],
    ["mainGameEntries", number.format(status.mainGameEntries)],
    ["unclassifiedEntries", number.format(status.unclassifiedEntries)],
    ["lastSuccessfulAt", date(status.lastSuccessfulAt)],
    ["failureCount", number.format(status.failureCount)],
    ["lastErrorCode", status.lastErrorCode ?? t("none")],
    ["nextAttemptAt", status.syncStatus === "FAILED" && !status.nextAttemptAt ? t("autoRetryStopped") : date(status.nextAttemptAt)]
  ] : [];
  return <div className="mt-4">
    <GameStatus {...result} onRetry={result.reload} />
    {status && <>
      <p className="mb-4 flex items-center gap-2 text-sm">{t("fields.syncStatus")}: <Badge tone={tone}>{t.has(`syncStatuses.${status.syncStatus}`) ? t(`syncStatuses.${status.syncStatus}`) : status.syncStatus}</Badge></p>
      <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{rows.map(([key, value]) => <div key={key} className="min-w-0"><dt className="text-sm text-text-secondary">{t(`statusFields.${key}`)}</dt><dd className="mt-1 break-words tabular-nums">{value}</dd></div>)}</dl>
      <p className="mt-4 text-sm text-text-secondary">{status.providerTotal == null ? t("providerTotalUnknown") : t("providerTotal", { count: number.format(status.providerTotal) })}</p>
      <button type="button" className={`${controlClass} mt-4`} onClick={result.reload}>{t("refreshStatus")}</button>
    </>}
  </div>;
}

/** Herkese açık katalog indeks durumu. Yalnız açıldığında istek atar; HTTP üzerinden senkronizasyon başlatma/durdurma yoktur. */
export function GameCatalogStatus({ defaultOpen = false }: { defaultOpen?: boolean }) {
  const t = useTranslations("Game");
  const id = useId();
  const [open, setOpen] = useState(defaultOpen);
  return <section aria-labelledby={`${id}-title`} className="rounded-panel border border-border bg-surface p-5">
    <h2 id={`${id}-title`} className="font-display text-lg font-semibold"><button type="button" aria-expanded={open} aria-controls={`${id}-body`} onClick={() => setOpen(!open)} className="min-h-11 text-left">{t("catalogStatus")}</button></h2>
    {open && <div id={`${id}-body`}><p className="text-sm text-text-secondary">{t("catalogStatusHelp")}</p><StatusBody /></div>}
  </section>;
}
