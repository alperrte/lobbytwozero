"use client";
import { useState } from "react";
import { useTranslations } from "next-intl";
import { useHardwareMutation } from "../hooks/use-hardware-query";
import { hardwareApi } from "../services/hardware-api";
import { specKeys, type Comparison, type Component } from "../types/hardware";
import { button, input, Field, HardwareDialog, HardwareError } from "./hardware-ui";

export function ComponentComparison({ profileId, components }: { profileId: string; components: Component[] }) {
  const t = useTranslations("Hardware");
  const [open, setOpen] = useState(false);
  const [leftId, setLeftId] = useState(components[0]?.id ?? "");
  const [rightId, setRightId] = useState("");
  const [result, setResult] = useState<Comparison>();
  const mutation = useHardwareMutation();
  const left = components.find((item) => item.id === leftId);
  return <>
    <button className={button} disabled={components.length < 2} onClick={() => setOpen(true)}>{t("comparison.components")}</button>
    {open ? <HardwareDialog title={t("comparison.components")} onClose={() => setOpen(false)} busy={mutation.pending}>
      <p className="text-sm text-text-secondary">{t("comparison.componentHint")}</p>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t("comparison.left")}>
          <select className={input} value={leftId} onChange={(event) => { setLeftId(event.target.value); setRightId(""); setResult(undefined); }}>{components.map((item) => <option key={item.id} value={item.id}>{item.modelName}</option>)}</select>
        </Field>
        <Field label={t("comparison.right")}>
          <select className={input} value={rightId} onChange={(event) => { setRightId(event.target.value); setResult(undefined); }}>
            <option value="">{t("comparison.select")}</option>
            {components.filter((item) => item.id !== leftId && item.category === left?.category).map((item) => <option key={item.id} value={item.id}>{item.modelName}</option>)}
          </select>
        </Field>
      </div>
      <button className={button} disabled={!leftId || !rightId || mutation.pending} onClick={async () => { setResult(undefined); const response = await mutation.run(() => hardwareApi.compareComponents(profileId, leftId, rightId)); if (response) setResult(response); }}>{t("comparison.compare")}</button>
      <HardwareError error={mutation.error} />
      {result ? <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <caption className="mb-3 text-start text-text-secondary">{t("comparison.facts")}</caption>
          <thead>
            <tr>
              <th className="p-2 text-start">{t("comparison.property")}</th>
              {[result.left, result.right].map((item) => <th className="min-w-32 p-2 text-start" key={item.id}>{item.modelName}</th>)}
            </tr>
          </thead>
          <tbody>{specKeys.map((key) => <tr className="border-t border-border" key={key}>
            <th className="p-2 text-start font-normal text-text-secondary">{t(`specs.${key}`)}</th>
            {[result.left, result.right].map((item) => <td className="p-2" key={item.id}>{item.specifications[key] ?? t("common.unknown")}</td>)}
          </tr>)}</tbody>
        </table>
      </div> : null}
    </HardwareDialog> : null}
  </>;
}
