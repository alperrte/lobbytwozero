"use client";
import { useTranslations } from "next-intl";
import { hardwareApi } from "../services/hardware-api";
import { useHardwareMutation, useHardwareQuery } from "../hooks/use-hardware-query";
import { categories, privacyFields, type Privacy, type Visibility } from "../types/hardware";
import { primaryButton, input, Field, HardwareDialog, HardwareError, HardwareLoading } from "./hardware-ui";

export function PrivacyForm({ profileId, onSaved, onClose }: { profileId: string; onSaved: () => void; onClose: () => void }) {
  const t = useTranslations("Hardware");
  const result = useHardwareQuery(`privacy:${profileId}`, (signal) => hardwareApi.privacy(profileId, signal));
  const mutation = useHardwareMutation(() => { result.reload(); onSaved(); });
  function select(name: string, value?: Visibility, inherit = true) {
    return <select className={input} name={name} defaultValue={value ?? ""}>
      {inherit ? <option value="">{t("privacy.inherit")}</option> : null}
      {["PRIVATE", "FRIENDS", "PUBLIC"].map((visibility) => <option key={visibility} value={visibility}>{t(`visibility.${visibility}`)}</option>)}
    </select>;
  }
  return <HardwareDialog title={t("privacy.title")} onClose={onClose} busy={mutation.pending}>
    <p className="text-sm leading-6 text-text-secondary">{t("privacy.description")}</p>
    {result.loading ? <HardwareLoading /> : null}
    <HardwareError error={result.error} retry={result.reload} />
    {result.data ? <form key={result.data.profileVersion} className="space-y-5" onSubmit={async (event) => { event.preventDefault(); const form = new FormData(event.currentTarget); const fields: Privacy["fields"] = {}; const categoryValues: Privacy["categories"] = {}; privacyFields.forEach((key) => { const value = form.get(`field:${key}`); if (value) fields[key] = value as Visibility; }); categories.forEach((key) => { const value = form.get(`category:${key}`); if (value) categoryValues[key] = value as Visibility; }); const response = await mutation.run(() => hardwareApi.savePrivacy(profileId, { profileVersion: result.data!.profileVersion, visibility: form.get("visibility") as Visibility, fields, categories: categoryValues })); if (response) onClose(); }}>
      <Field label={t("privacy.profileVisibility")}>{select("visibility", result.data.visibility, false)}</Field>
      <fieldset className="space-y-4">
        <legend className="mb-3 font-display font-semibold">{t("privacy.fields")}</legend>
        {privacyFields.map((key) => <Field label={t(`privacyFields.${key}`)} key={key}>{select(`field:${key}`, result.data!.fields[key])}</Field>)}
      </fieldset>
      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-3 font-display font-semibold">{t("privacy.categories")}</legend>
        {categories.map((key) => <Field label={t(`categories.${key}`)} key={key}>{select(`category:${key}`, result.data!.categories[key])}</Field>)}
      </fieldset>
      <HardwareError error={mutation.error} />
      <button className={primaryButton} disabled={mutation.pending || mutation.requiresReview}>{t(mutation.pending ? "common.saving" : "common.save")}</button>
    </form> : null}
  </HardwareDialog>;
}
