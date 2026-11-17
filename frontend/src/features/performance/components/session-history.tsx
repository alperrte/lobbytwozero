"use client";

import { useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { usePerformanceQuery } from "../hooks/use-performance-query";
import { performanceApi } from "../services/performance-api";
import type { SessionStatus } from "../types/performance";
import { control, panel, select, PerformanceError, PerformanceLoading } from "./performance-ui";
import { SessionMeasurements } from "./session-measurements";
import { SessionReport } from "./session-report";
import { DeletePerformanceData } from "./delete-performance-data";

export function SessionHistory({ accountId }: { accountId: string }) {
  const t = useTranslations("Performance");
  const format = useFormatter();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<SessionStatus | "">("");
  const [selectedId, setSelectedId] = useState("");
  const [deleted, setDeleted] = useState(false);
  const result = usePerformanceQuery(`sessions:${accountId}:${status}:${page}`, (signal) => performanceApi.sessions({ page, status: status || undefined }, signal));
  const selected = result.data?.items.find((item) => item.id === selectedId);

  return <section aria-labelledby="performance-history" className="space-y-4">
    <h2 id="performance-history" className="font-display text-2xl font-semibold">{t("history")}</h2>
    <p className="text-sm text-text-secondary">{t("historyDescription")}</p>
    {deleted ? <p role="status" className="text-sm text-success">{t("deleted")}</p> : null}
    <label className="flex flex-wrap items-center gap-3 text-sm"><span>{t("statusFilter")}</span>
      <select className={select} value={status} onChange={(event) => { setStatus(event.target.value as SessionStatus | ""); setPage(0); setSelectedId(""); }}>
        <option value="">{t("allStatuses")}</option>
        {(["ACTIVE", "COMPLETED", "ABANDONED"] as const).map((value) => <option key={value} value={value}>{t(`sessionStatus.${value}`)}</option>)}
      </select>
    </label>
    {result.loading ? <PerformanceLoading /> : result.error ? <PerformanceError error={result.error} retry={result.reload} /> : result.data ? <>
      {!result.data.items.length ? <p className={panel}>{t("historyEmpty")}</p> : <ul className="space-y-3">{result.data.items.map((item) => <li key={item.id} className={panel}>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <h3 className="font-semibold break-words">{item.gameTitle}</h3>
            <p className="mt-2 text-sm text-text-secondary">{format.dateTime(new Date(item.startedAt), { dateStyle: "medium", timeStyle: "short" })} · {t(`sessionStatus.${item.status}`)} · {t("manual")}</p>
            <p className="mt-1 text-sm text-text-secondary">{t("sampleCount", { count: item.sampleCount })} · {item.durationSeconds === null ? t("ongoing") : t("duration", { seconds: format.number(item.durationSeconds) })}</p>
          </div>
          <button className={control} aria-pressed={selectedId === item.id} aria-label={t("openSession", { game: item.gameTitle })} onClick={() => setSelectedId(item.id)}>{t("viewSession")}</button>
        </div>
      </li>)}</ul>}
      <nav aria-label={t("historyPagination")} className="flex flex-wrap items-center justify-between gap-3">
        <button className={control} disabled={page === 0} onClick={() => { setPage(page - 1); setSelectedId(""); }}>{t("previous")}</button>
        <p className="text-sm">{t("historyPage", { page: page + 1, total: result.data.totalElements })}</p>
        <button className={control} disabled={!result.data.hasNext} onClick={() => { setPage(page + 1); setSelectedId(""); }}>{t("next")}</button>
      </nav>
    </> : null}
    {selected ? <div key={selected.id} className="space-y-6 border-t border-border pt-6">
      <h3 className="font-display text-xl font-semibold break-words">{t("sessionDetails", { game: selected.gameTitle })}</h3>
      <SessionMeasurements accountId={accountId} session={selected} />
      <SessionReport accountId={accountId} session={selected} />
      <DeletePerformanceData session={selected} onDeleted={() => { setSelectedId(""); setDeleted(true); setPage(0); result.reload(); }} onReview={() => { setSelectedId(""); result.reload(); }} />
    </div> : null}
  </section>;
}
