"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { useHardwareQuery } from "../hooks/use-hardware-query";
import { hardwareApi } from "../services/hardware-api";
import { HardwareEmpty, HardwareError, HardwareLoading, HardwarePagination, button, panel, input } from "./hardware-ui";
import { SpecificationTable } from "./specification-table";

export function HardwareSnapshot({ accountId }: { accountId: string }) {
  const t = useTranslations("Performance");
  const hw = useTranslations("Hardware");
  const [page, setPage] = useState(0);
  const [chosen, setChosen] = useState("");
  const [componentPage, setComponentPage] = useState(0);
  const profiles = useHardwareQuery(`performance-profiles:${accountId}:${page}`, (signal) => hardwareApi.profiles(page, signal));
  const profileId = profiles.data?.items.some((item) => item.id === chosen) ? chosen : profiles.data?.items[0]?.id ?? "";
  const detail = useHardwareQuery(`performance-profile:${accountId}:${profileId}:${componentPage}`, (signal) => hardwareApi.profile(profileId, componentPage, signal), Boolean(profileId));
  const privacy = useHardwareQuery(`performance-privacy:${accountId}:${profileId}`, (signal) => hardwareApi.privacy(profileId, signal), Boolean(profileId));

  return <section aria-labelledby="performance-hardware" className="space-y-4">
    <h2 id="performance-hardware" className="font-display text-2xl font-semibold">{t("hardware")}</h2>
    <Link href="/profile/computers" className={button}>{t("manageHardware")}</Link>
    {profiles.loading ? <HardwareLoading /> : profiles.error ? <HardwareError error={profiles.error} retry={profiles.reload} /> : !profiles.data?.items.length ? <HardwareEmpty title={t("hardwareEmpty")} description={t("hardwareEmptyDescription")} /> : <>
      <label className="block max-w-lg space-y-2"><span className="block text-sm">{t("computer")}</span>
        <select className={input} value={profileId} onChange={(event) => { setChosen(event.target.value); setComponentPage(0); }}>
          {profiles.data.items.map((profile) => <option key={profile.id} value={profile.id}>{profile.name}</option>)}
        </select>
      </label>
      <HardwarePagination page={page} totalPages={profiles.data.totalPages} onPage={(next) => { setPage(next); setChosen(""); setComponentPage(0); }} />
      {privacy.loading ? <HardwareLoading /> : privacy.error ? <HardwareError error={privacy.error} retry={privacy.reload} /> : privacy.data ? <p className="text-sm text-text-secondary">{t("visibility", { value: hw(`visibility.${privacy.data.visibility}`) })}</p> : null}
      {detail.loading ? <HardwareLoading /> : detail.error ? <HardwareError error={detail.error} retry={detail.reload} /> : detail.data ? <>
        <p className="text-sm text-text-secondary">{t("hardwareSource", { value: t(detail.data.profile.source === "MANUAL" ? "manual" : detail.data.profile.source === "DESKTOP_SYNC" ? "synced" : "unknownSource") })}</p>
        {detail.data.components.length ? <ul className="grid gap-4 lg:grid-cols-2">{detail.data.components.map((component) => <li key={component.id} className={`${panel} p-5`}>
          <h3 className="font-semibold break-words">{hw(`categories.${component.category}`)} · {component.modelName}</h3>
          <p className="my-3 text-sm text-text-secondary">{t("componentMeta", { brand: component.brandName ?? t("brandUnspecified"), quantity: component.quantity, source: t(component.source === "MANUAL" ? "manual" : component.source === "DESKTOP_SYNC" ? "synced" : "unknownSource") })}</p>
          <SpecificationTable specifications={component.specifications} />
        </li>)}</ul> : <p className={panel + " p-5"}>{t("componentsEmpty")}</p>}
        <HardwarePagination page={componentPage} totalPages={detail.data.totalPages} onPage={setComponentPage} />
      </> : null}
    </>}
  </section>;
}
