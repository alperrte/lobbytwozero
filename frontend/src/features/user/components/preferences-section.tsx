"use client";

import { useId, useState, type FormEvent } from "react";
import { useLocale, useTranslations } from "next-intl";
import { usePathname, useRouter } from "@/shared/lib/i18n/navigation";
import type { AppPathname } from "@/shared/lib/i18n/routing";
import type { ThemePreference } from "@/shared/lib/preferences/preferences";
import { changeTheme } from "@/shared/lib/theme/theme-transition";
import { savePreferences } from "../services/settings-api";
import { locales, themes, type Preferences, type SettingsLocale, type Theme } from "../types/settings.types";
import { SaveBar, SelectRow, settingsErrorKey, type SaveMessage } from "./settings-parts";

const nativeNames: Record<SettingsLocale, string> = { tr: "Türkçe", en: "English" };

const themeToDevice: Record<Theme, ThemePreference> = { SYSTEM: "system", LIGHT: "light", DARK: "dark" };

/**
 * Hesap tercihleri (USER-10): arayüz dili ve tema. Kayıt hesaba yazılır; kayıt başarılı olunca seçim bu cihaza da uygulanır.
 * Hareket azaltma ve sıkı yerleşim tercihlerini arayüz henüz kullanmaz, bu yüzden gösterilmez; kaydederken sunucudaki değer
 * olduğu gibi geri gönderilir (tercihler birlikte yazılır).
 */
export function PreferencesSection({ initial, onRefresh }: { initial: Preferences; onRefresh: () => void }) {
  const t = useTranslations("AccountSettings.preferences");
  const activeLocale = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const [saved, setSaved] = useState(initial);
  const [draft, setDraft] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<SaveMessage | null>(null);
  const baseId = useId();

  const localeOptions = locales.map((value) => ({ value, label: nativeNames[value] }));
  const themeOptions = themes.map((value) => ({ value, label: t(`theme.options.${value}`) }));

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setMessage(null);
    try {
      const result = await savePreferences({ ...draft, version: saved.version });
      setSaved(result);
      setDraft(result);
      setMessage({ kind: "ok", key: "saved" });
      changeTheme(themeToDevice[result.theme]);
      if (result.locale !== activeLocale) {
        router.replace(pathname as AppPathname, { locale: result.locale });
      }
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
        <SelectRow
          id={`${baseId}-locale`}
          label={t("locale.label")}
          hint={t("locale.hint")}
          value={draft.locale}
          options={localeOptions}
          disabled={busy}
          onChange={(value) => setDraft({ ...draft, locale: value as SettingsLocale })}
        />
        <SelectRow
          id={`${baseId}-theme`}
          label={t("theme.label")}
          hint={t("theme.hint")}
          value={draft.theme}
          options={themeOptions}
          disabled={busy}
          onChange={(value) => setDraft({ ...draft, theme: value as Theme })}
        />
      </div>
      <SaveBar busy={busy} message={message} onRefresh={onRefresh} />
    </form>
  );
}
