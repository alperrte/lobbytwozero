"use client";
import { useState } from "react";
import { useTranslations } from "next-intl";
import { hardwareApi } from "../services/hardware-api";
import { useHardwareMutation } from "../hooks/use-hardware-query";
import type { Comparison, Profile, ProfileComparisonItem } from "../types/hardware";
import { categories } from "../types/hardware";
import { button, HardwareDialog, HardwareError } from "./hardware-ui";

export function ProfileComparison({ profiles, clear }: { profiles: Profile[]; clear: () => void }) {
  const t = useTranslations("Hardware");
  const [open, setOpen] = useState(false);
  const [result, setResult] = useState<Comparison<ProfileComparisonItem>>();
  const mutation = useHardwareMutation();
  return <>
    <div className="flex flex-wrap gap-2">
      <button className={button} disabled={profiles.length !== 2 || mutation.pending} onClick={async () => { setResult(undefined); setOpen(true); const response = await mutation.run(() => hardwareApi.compareProfiles(profiles[0].id, profiles[1].id)); if (response) setResult(response); }}>{t("comparison.compare")}</button>
      <button className={button} disabled={!profiles.length} onClick={clear}>{t("common.clear")}</button>
    </div>
    {open ? <HardwareDialog title={t("comparison.title")} busy={mutation.pending} onClose={() => setOpen(false)}>
      <HardwareError error={mutation.error} />
      {mutation.pending ? <p role="status">{t("common.loading")}</p> : null}
      {result ? <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <caption className="mb-4 text-start text-text-secondary">{t("comparison.facts")}</caption>
          <thead>
            <tr>
              <th className="p-2 text-start">{t("comparison.property")}</th>
              {profiles.map((profile) => <th key={profile.id} className="min-w-32 break-words p-2 text-start">{profile.name}</th>)}
            </tr>
          </thead>
          <tbody>
            {categories.map((category) => <tr className="border-t border-border" key={category}>
              <th className="p-2 text-start font-normal text-text-secondary">{t(`categories.${category}`)}</th>
              {[result.left, result.right].map((item) => <td key={item.profileId} className="p-2">{item.categories[category]?.units ?? t("common.unknown")}</td>)}
            </tr>)}
            {["ram", "storage"].map((key) => <tr key={key} className="border-t border-border">
              <th className="p-2 text-start font-normal">{t(`stats.${key}`)}</th>
              {[result.left, result.right].map((item) => <td key={item.profileId} className="p-2">
                {t("stats.capacity", { value: item[key as "ram" | "storage"].knownCapacityGb })}
                <p className="text-xs text-text-secondary">{t("stats.unknownUnits", { count: item[key as "ram" | "storage"].unitsWithoutCapacity })}</p>
              </td>)}
            </tr>)}
          </tbody>
        </table>
      </div> : null}
    </HardwareDialog> : null}
  </>;
}
