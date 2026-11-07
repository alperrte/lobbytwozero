"use client";
import { useState } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { hardwareApi } from "../services/hardware-api";
import { useHardwareQuery } from "../hooks/use-hardware-query";
import type { Profile } from "../types/hardware";
import { ProfileForm } from "./profile-form";
import { ProfileWorkspace } from "./profile-workspace";
import { ProfileComparison } from "./profile-comparison";
import { button, primaryButton, panel, HardwareEmpty, HardwareError, HardwareHeading, HardwareLoading, HardwarePagination } from "./hardware-ui";

export function ProfilesScreen() {
  const t = useTranslations("Hardware");
  const [page, setPage] = useState(0);
  const [active, setActive] = useState<string>();
  const [creating, setCreating] = useState(false);
  const [compared, setCompared] = useState<Profile[]>([]);
  const profiles = useHardwareQuery(`profiles:${page}`, async (signal) => {
    const response = await hardwareApi.profiles(page, signal);
    if (!signal.aborted) setActive((current) => current ?? response.items[0]?.id);
    return response;
  });
  const statistics = useHardwareQuery("statistics", (signal) => hardwareApi.statistics(signal));
  function refresh(profile?: Profile) { profiles.reload(); statistics.reload(); setCompared([]); if (profile) setActive(profile.id); }
  const selectedId = profiles.error ? undefined : active ?? profiles.data?.items[0]?.id;
  return <>
    <HardwareHeading title={t("profiles.title")} description={t("profiles.description")}>{profiles.data ? <button className={primaryButton} onClick={() => setCreating(true)}>{t("profiles.create")}</button> : null}</HardwareHeading>
    <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_15rem]">
      <div className="min-w-0 space-y-5">
        {profiles.loading ? <HardwareLoading /> : null}
        <HardwareError error={profiles.error} retry={() => { profiles.reload(); statistics.reload(); }} />
        {profiles.data?.items.length === 0 && !selectedId ? <HardwareEmpty title={t("profiles.emptyTitle")} description={t("profiles.emptyDescription")} /> : null}
        {profiles.data?.items.length ? <div className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2">{profiles.data.items.map((profile) => <article key={profile.id} className={`${panel} p-4 ${selectedId === profile.id ? "border-primary" : ""}`}>
            <Icon name="desktop" className="mb-4 size-7 text-text-secondary" />
            <p className="text-xs text-text-secondary">{t(`devices.${profile.deviceType}`)}</p>
            <button className="mt-1 min-h-11 w-full break-words text-start font-display text-lg font-semibold hover:underline" aria-pressed={selectedId === profile.id} onClick={() => setActive(profile.id)}>{profile.name}</button>
            <label className="mt-3 flex min-h-11 items-center gap-2 text-xs text-text-secondary">
              <input type="checkbox" checked={compared.some((item) => item.id === profile.id)} disabled={compared.length === 2 && !compared.some((item) => item.id === profile.id)} onChange={(event) => setCompared(event.target.checked ? [...compared, profile] : compared.filter((item) => item.id !== profile.id))} />
              {t("comparison.add")}
            </label>
          </article>)}</div>
          <HardwarePagination page={page} totalPages={profiles.data.totalPages} onPage={setPage} />
          {compared.length ? <ProfileComparison profiles={compared} clear={() => setCompared([])} /> : null}
        </div> : null}
        {selectedId ? <ProfileWorkspace key={selectedId} profileId={selectedId} onChanged={refresh} onDeleted={() => { setActive(undefined); setPage(0); refresh(); }} /> : null}
      </div>
      <aside className="space-y-5">
        <section className={`${panel} p-5`}>
          <h2 className="font-display text-lg font-semibold">{t("stats.title")}</h2>
          {statistics.loading ? <p role="status" className="mt-3 text-sm text-text-secondary">{t("common.loading")}</p> : null}
          <HardwareError error={statistics.error} retry={statistics.reload} />
          {statistics.data ? <dl className="mt-4 divide-y divide-border">
            {[["profiles", statistics.data.totalProfiles], ["entries", statistics.data.totalComponentEntries], ["units", statistics.data.totalComponentUnits]].map(([key, value]) => <div key={key} className="flex items-center justify-between gap-2 py-3">
              <dt className="text-sm text-text-secondary">{t(`stats.${key}`)}</dt>
              <dd className="font-display text-xl font-semibold">{value}</dd>
            </div>)}
            {["ram", "storage"].map((key) => <div key={key} className="py-3">
              <dt className="text-sm text-text-secondary">{t(`stats.${key}`)}</dt>
              <dd className="mt-1 font-display text-xl font-semibold">{t("stats.capacity", { value: key === "ram" ? statistics.data!.recordedRamCapacityGb : statistics.data!.recordedStorageCapacityGb })}</dd>
              <dd className="mt-1 text-xs text-text-secondary">{t("stats.unknownUnits", { count: key === "ram" ? statistics.data!.ramUnitsWithoutCapacity : statistics.data!.storageUnitsWithoutCapacity })}</dd>
            </div>)}
          </dl> : null}
        </section>
        <section className={`${panel} p-5`}>
          <Icon name="desktop" className="mb-4 size-6 text-text-secondary" />
          <h2 className="font-display font-semibold">{t("profiles.syncTitle")}</h2>
          <p className="mt-2 text-sm leading-6 text-text-secondary">{t("profiles.syncDescription")}</p>
        </section>
      </aside>
    </div>
    {creating ? <ProfileForm onClose={() => setCreating(false)} onSaved={refresh} /> : null}
    {!profiles.loading && !profiles.data ? <button className={`${button} mt-5`} onClick={profiles.reload}>{t("common.reload")}</button> : null}
  </>;
}
