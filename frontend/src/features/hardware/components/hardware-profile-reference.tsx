"use client";

import { useTranslations } from "next-intl";
import { useHardwareQuery } from "../hooks/use-hardware-query";
import { hardwareApi } from "../services/hardware-api";
import { HardwareError } from "./hardware-ui";

/** Read the actual owner profile; never substitute the dashboard's currently selected computer. */
export function HardwareProfileReference({ accountId, profileId }: { accountId: string; profileId: string }) {
  const t = useTranslations("Performance");
  const result = useHardwareQuery(`performance-report-profile:${accountId}:${profileId}`, (signal) => hardwareApi.profile(profileId, 0, signal));
  if (result.loading) return <p role="status" className="mt-3 text-sm">{t("loading")}</p>;
  if (result.error) return <HardwareError error={result.error} retry={result.reload} />;
  return result.data ? <p className="mt-3 text-sm">{t("reportComputer", { name: result.data.profile.name })}</p> : null;
}
