"use client";

import { useId } from "react";
import { useFormatter, useTranslations } from "next-intl";
import type { Series } from "../types/performance";
import { panel } from "./performance-ui";

/** Coordinates come only from returned points; no interpolation of missing metrics or zero filling. */
export function MeasurementChart({ series }: { series: Series }) {
  const t = useTranslations("Performance");
  const format = useFormatter();
  const id = useId();
  if (!series.points.length) return <p className={panel}>{t("measurementsEmpty")}</p>;
  const times = series.points.map((point) => new Date(point.at).getTime());
  const minTime = Math.min(...times);
  const maxTime = Math.max(...times);
  const minValue = Math.min(...series.points.map((point) => point.value));
  const maxValue = Math.max(...series.points.map((point) => point.value));
  const x = (time: number) => 60 + (maxTime === minTime ? 260 : (time - minTime) / (maxTime - minTime) * 520);
  const y = (value: number) => maxValue === minValue ? 125 : 215 - (value - minValue) / (maxValue - minValue) * 180;
  const label = t("chartLabel", { metric: t(`metrics.${series.metric}`), unit: series.unit });
  const date = (at: string) => format.dateTime(new Date(at), { dateStyle: "short", timeStyle: "medium" });
  return <div className={`${panel} min-w-0 space-y-4`}>
    <p id={`${id}-summary`} className="text-sm text-text-secondary">{series.downsampled ? t("aggregated", { count: series.totalSamples }) : t("raw", { count: series.totalSamples })}</p>
    <div className="overflow-x-auto" role="region" aria-label={label} tabIndex={0}>
    <svg viewBox="0 0 640 270" role="img" aria-labelledby={`${id}-title`} aria-describedby={`${id}-summary`} className="w-full min-w-[40rem] text-text-primary">
      <title id={`${id}-title`}>{label}</title>
      <path d="M60 25V215H600" fill="none" stroke="currentColor" opacity="0.4" />
      <text x="5" y="20" fill="currentColor" fontSize="13">{series.unit}</text>
      <text x="5" y="45" fill="currentColor" fontSize="12">{format.number(maxValue, { maximumFractionDigits: 1 })}</text>
      {maxValue !== minValue ? <text x="5" y="215" fill="currentColor" fontSize="12">{format.number(minValue, { maximumFractionDigits: 1 })}</text> : null}
      <polyline points={series.points.map((point, index) => `${x(times[index])},${y(point.value)}`).join(" ")} fill="none" stroke="currentColor" strokeWidth="2" className="text-primary" />
      {series.points.map((point, index) => <circle key={point.at} cx={x(times[index])} cy={y(point.value)} r="3" fill="currentColor" />)}
      <text x="60" y="240" fill="currentColor" fontSize="11">{date(series.points[0].at)}</text>
      {maxTime !== minTime ? <text x="600" y="258" textAnchor="end" fill="currentColor" fontSize="11">{date(series.points[series.points.length - 1].at)}</text> : null}
    </svg>
    </div>
    <div className="overflow-x-auto" role="region" aria-label={t("dataTable")} tabIndex={0}>
      <table className="w-full text-start text-sm">
        <caption className="mb-3 text-start font-semibold">{label}</caption>
        <thead><tr>{["timestamp", "value", "minimum", "maximum", "samples"].map((key) => <th key={key} scope="col" className="whitespace-nowrap px-3 py-2 text-start">{t(key)}</th>)}</tr></thead>
        <tbody>{series.points.map((point) => <tr key={point.at} className="border-t border-border"><th scope="row" className="whitespace-nowrap px-3 py-2 text-start font-normal">{date(point.at)}</th>{[point.value, point.min, point.max, point.count].map((value, index) => <td key={index} className="px-3 py-2">{format.number(value, { maximumFractionDigits: 2 })}</td>)}</tr>)}</tbody>
      </table>
    </div>
  </div>;
}
