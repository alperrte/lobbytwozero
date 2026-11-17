"use client";

import { useFormatter, useTranslations } from "next-intl";
import { ApiHttpError } from "@/shared/lib/api/client";
import { Link } from "@/shared/lib/i18n/navigation";
import { HardwareProfileReference } from "@/features/hardware";
import { usePerformanceQuery } from "../hooks/use-performance-query";
import { performanceApi } from "../services/performance-api";
import type { PerformanceSession } from "../types/performance";
import { panel, PerformanceError, PerformanceLoading } from "./performance-ui";

const reportFields = [
  ["avgFps", "FPS"], ["p1LowFps", "FPS"], ["maxCpuTempC", "°C"],
  ["maxGpuTempC", "°C"], ["avgCpuUsagePercent", "%"], ["avgGpuUsagePercent", "%"]
] as const;

export function SessionReport({ accountId, session }: { accountId: string; session: PerformanceSession }) {
  const t = useTranslations("Performance");
  const format = useFormatter();
  const result = usePerformanceQuery(`report:${accountId}:${session.id}`, (signal) => performanceApi.report(session.id, signal));
  const missing = result.error instanceof ApiHttpError && result.error.status === 404;
  return <section aria-labelledby="performance-report" className="space-y-4">
    <h4 id="performance-report" className="font-display text-lg font-semibold">{t("report")}</h4>
    {result.loading ? <PerformanceLoading /> : missing ? <p className={panel}>{t("reportEmpty")}</p> : result.error ? <PerformanceError error={result.error} retry={result.reload} /> : result.data ? <div className={panel}>
      <p className="mb-4 text-sm text-text-secondary">{t("manualReport")}</p>
      <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div><dt className="text-sm text-text-secondary">{t("resolution")}</dt><dd>{result.data.resolution}</dd></div>
        <div><dt className="text-sm text-text-secondary">{t("preset")}</dt><dd>{t(`presets.${result.data.graphicsPreset}`)}</dd></div>
        {reportFields.map(([field, unit]) => <div key={field}><dt className="text-sm text-text-secondary">{t(`reportFields.${field}`)}</dt><dd>{result.data?.[field] == null ? t("notRecorded") : `${format.number(result.data[field], { maximumFractionDigits: 2 })} ${unit}`}</dd></div>)}
      </dl>
      {result.data.notes ? <p className="mt-5 whitespace-pre-wrap break-words text-sm">{result.data.notes}</p> : null}
      <p className="mt-4 text-sm text-text-secondary">{t("updatedAt", { value: format.dateTime(new Date(result.data.updatedAt), { dateStyle: "medium", timeStyle: "short" }) })}</p>
      <p className="mt-3 text-sm">{session.hardwareProfileId ? t("linkedHardware") : t("noLinkedHardware")} {session.hardwareProfileId ? <Link className="underline" href="/profile/computers">{t("manageHardware")}</Link> : null}</p>
      {session.hardwareProfileId ? <HardwareProfileReference accountId={accountId} profileId={session.hardwareProfileId} /> : null}
    </div> : null}
  </section>;
}
