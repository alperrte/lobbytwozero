"use client";
import { useTranslations } from "next-intl";
import { useHardwareMutation } from "../hooks/use-hardware-query";
import { safeTextPattern } from "../schemas/hardware";
import { hardwareApi } from "../services/hardware-api";
import type { DeviceType, Profile } from "../types/hardware";
import { button, primaryButton, input, Field, HardwareDialog, HardwareError } from "./hardware-ui";

export function ProfileForm({ profile, onClose, onSaved }: { profile?: Profile; onClose: () => void; onSaved: (profile?: Profile) => void }) {
  const t = useTranslations("Hardware");
  const mutation = useHardwareMutation(() => onSaved());
  return <HardwareDialog title={t(profile ? "profiles.edit" : "profiles.create")} onClose={onClose} busy={mutation.pending}>
    <form className="space-y-5" onSubmit={async (event) => { event.preventDefault(); const data = new FormData(event.currentTarget); const body = { name: String(data.get("name")).trim(), deviceType: String(data.get("deviceType")) as DeviceType }; const response = await mutation.run(() => profile ? hardwareApi.updateProfile(profile.id, { ...body, profileVersion: profile.version }) : hardwareApi.createProfile(body)); if (response) { onSaved(response); onClose(); } }}>
      <Field label={t("profiles.name")}>
        <input autoFocus required name="name" maxLength={100} pattern={safeTextPattern} defaultValue={profile?.name} className={input} onInput={(event) => event.currentTarget.setCustomValidity(event.currentTarget.value.trim() ? "" : t("common.required"))} />
      </Field>
      <Field label={t("profiles.deviceType")}>
        <select name="deviceType" className={input} defaultValue={profile?.deviceType ?? "DESKTOP"}>{["DESKTOP", "LAPTOP", "OTHER"].map((type) => <option key={type} value={type}>{t(`devices.${type}`)}</option>)}</select>
      </Field>
      {!profile ? <p className="text-sm leading-6 text-text-secondary">{t("profiles.nextStep")}</p> : null}
      <HardwareError error={mutation.error} />
      <div className="flex flex-wrap justify-end gap-2">
        <button type="button" className={button} disabled={mutation.pending} onClick={onClose}>{t("common.cancel")}</button>
        <button className={primaryButton} disabled={mutation.pending || mutation.requiresReview}>{t(mutation.pending ? "common.saving" : "common.save")}</button>
      </div>
    </form>
  </HardwareDialog>;
}
