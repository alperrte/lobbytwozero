"use client";

import { useFormatter, useTranslations } from "next-intl";
import { useSession } from "@/features/auth";
import { Link } from "@/shared/lib/i18n/navigation";
import { usePerformanceQuery } from "../hooks/use-performance-query";
import { performanceApi } from "../services/performance-api";
import { control, panel, PerformanceError, PerformanceLoading } from "./performance-ui";

export function DeviceList() {
  const t = useTranslations("Performance");
  const format = useFormatter();
  const session = useSession();
  const result = usePerformanceQuery(`devices:${session.me?.accountId ?? "guest"}`, performanceApi.devices, session.status === "authenticated");
  if (session.status === "loading") return <PerformanceLoading />;
  if (session.status === "guest") return <div className={panel}><p>{t("signIn")}</p><Link className={`${control} mt-3`} href="/login">{t("login")}</Link></div>;
  if (result.loading) return <PerformanceLoading />;
  if (result.error) return <PerformanceError error={result.error} retry={result.reload} />;
  if (!result.data?.length) return <p className={panel}>{t("devicesEmpty")}</p>;
  return <ul className="space-y-3">{result.data.map((device) => <li key={device.id} className={panel}>
    <h3 className="font-semibold break-words">{device.label}</h3>
    <p className="mt-2 text-sm text-text-secondary">{t(`deviceStatus.${device.status}`)}</p>
    <dl className="mt-3 grid gap-2 text-sm sm:grid-cols-2">
      <div><dt>{t("linkedAt")}</dt><dd>{format.dateTime(new Date(device.linkedAt), { dateStyle: "medium", timeStyle: "short" })}</dd></div>
      <div><dt>{t("lastSync")}</dt><dd>{device.lastSyncAt ? format.dateTime(new Date(device.lastSyncAt), { dateStyle: "medium", timeStyle: "short" }) : t("neverSynced")}</dd></div>
    </dl>
  </li>)}</ul>;
}
