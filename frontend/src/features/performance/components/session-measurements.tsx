"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { usePerformanceQuery } from "../hooks/use-performance-query";
import { performanceApi } from "../services/performance-api";
import { metrics, type Metric, type PerformanceSession } from "../types/performance";
import { panel, select, PerformanceError, PerformanceLoading } from "./performance-ui";
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
      <label className="grid gap-2 text-sm"><span>{t("metric")}</span><select className={select} value={metric} onChange={(event) => setMetric(event.target.value as Metric)}>{metrics.map((value) => <option key={value} value={value}>{t(`metrics.${value}`)}</option>)}</select></label>
      <label className="grid gap-2 text-sm"><span>{t("timeRange")}</span><select className={select} value={minutes} onChange={(event) => setMinutes(Number(event.target.value))}><option value={0}>{t("entireSession")}</option><option value={5}>{t("firstMinutes", { count: 5 })}</option><option value={30}>{t("firstMinutes", { count: 30 })}</option></select></label>
    </div>
    {result.loading ? <PerformanceLoading /> : result.error ? <PerformanceError error={result.error} retry={result.reload} /> : result.data?.points.length ? <MeasurementChart series={result.data} /> : <p className={panel}>{t("measurementsEmpty")}</p>}
  </section>;
}
