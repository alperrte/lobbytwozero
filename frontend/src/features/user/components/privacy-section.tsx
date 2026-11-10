"use client";

import { useId, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { savePrivacy } from "../services/settings-api";
import {
  guestAccesses,
  privacyFields,
  visibilities,
  type GuestAccess,
  type PrivacyField,
  type PrivacySettings,
  type Visibility
} from "../types/settings.types";
import { SaveBar, SelectRow, settingsErrorKey, type SaveMessage } from "./settings-parts";

/** Gizlilik ayarları (USER-06): her bölümün görünürlüğü ve girişsiz ziyaretçinin göreceği profil düzeyi. */
export function PrivacySection({ initial, onRefresh }: { initial: PrivacySettings; onRefresh: () => void }) {
  const t = useTranslations("AccountSettings.privacy");
  const [saved, setSaved] = useState(initial);
  const [draft, setDraft] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<SaveMessage | null>(null);
  const baseId = useId();

  const visibilityOptions = visibilities.map((value) => ({ value, label: t(`visibility.${value}`) }));
  const guestOptions = guestAccesses.map((value) => ({ value, label: t(`guest.options.${value}`) }));

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setMessage(null);
    try {
      const result = await savePrivacy({ ...draft, version: saved.version });
      // Sunucu yanıtı kesinleşmeden "kaydedildi" denmez; alanlar sunucunun döndürdüğü değere çekilir.
      setSaved(result);
      setDraft(result);
      setMessage({ kind: "ok", key: "saved" });
    } catch (error) {
      setMessage({ kind: "error", key: settingsErrorKey(error) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate>
      <p className="mb-5 max-w-2xl text-sm text-text-secondary">{t("description")}</p>
      <div className="flex flex-col gap-5">
        {privacyFields.map((field: PrivacyField) => (
          <SelectRow
            key={field}
            id={`${baseId}-${field}`}
            label={t(`fields.${field}.label`)}
            hint={t(`fields.${field}.hint`)}
            value={draft[field]}
            options={visibilityOptions}
            disabled={busy}
            onChange={(value) => setDraft({ ...draft, [field]: value as Visibility })}
          />
        ))}
        <SelectRow
          id={`${baseId}-guest`}
          label={t("guest.label")}
          hint={t("guest.hint")}
          value={draft.guestProfileAccess}
          options={guestOptions}
          disabled={busy}
          onChange={(value) => setDraft({ ...draft, guestProfileAccess: value as GuestAccess })}
        />
      </div>
      <SaveBar busy={busy} message={message} onRefresh={onRefresh} />
    </form>
  );
}
