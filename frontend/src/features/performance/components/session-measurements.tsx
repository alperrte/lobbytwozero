"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { usePerformanceQuery } from "../hooks/use-performance-query";
import { performanceApi } from "../services/performance-api";
import { metrics, type Metric, type PerformanceSession } from "../types/performance";
import { Select } from "@/shared/components/ui/select";
import { panel, PerformanceError, PerformanceLoading } from "./performance-ui";
import { MeasurementChart } from "./measurement-chart";

export function SessionMeasurements({ accountId, session }: { accountId: string; session: PerformanceSession }) {
  const t = useTranslations("Performance");
  const [metric, setMetric] = useState<Metric>("FPS");
  const [minutes, setMinutes] = useState(0);
  const from = minutes ? session.startedAt : undefined;
  const to = minutes ? new Date(new Date(session.startedAt).getTime() + minutes * 60000).toISOString() : undefined;
  const result = usePerformanceQuery(`series:${accountId}:${session.id}:${metric}:${minutes}`, (signal) => performanceApi.series(session.id, metric, from, to, signal));
  return <section aria-labelledby="performance-measurements" className="space-y-4">
    <h4 id="performance-measurements" className="font-display text-lg font-semibold">{t("measurements")}</h4>
    <div className="flex flex-wrap gap-4">
      <div className="grid w-56 max-w-full gap-2 text-sm"><span aria-hidden="true">{t("metric")}</span>
        <Select aria-label={t("metric")} value={metric} onChange={(value) => setMetric(value as Metric)} options={metrics.map((value) => ({ value, label: t(`metrics.${value}`) }))} />
      </div>
      <div className="grid w-56 max-w-full gap-2 text-sm"><span aria-hidden="true">{t("timeRange")}</span>
        <Select aria-label={t("timeRange")} value={String(minutes)} onChange={(value) => setMinutes(Number(value))}
          options={[{ value: "0", label: t("entireSession") }, { value: "5", label: t("firstMinutes", { count: 5 }) }, { value: "30", label: t("firstMinutes", { count: 30 }) }]} />
      </div>
    </div>
    {result.loading ? <PerformanceLoading /> : result.error ? <PerformanceError error={result.error} retry={result.reload} /> : result.data?.points.length ? <MeasurementChart series={result.data} /> : <p className={panel}>{t("measurementsEmpty")}</p>}
  </section>;
}
