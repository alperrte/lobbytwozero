"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { useLibrary } from "../hooks/use-library";
import { useLibraryMutation } from "../hooks/use-library-mutation";
import type { LibraryPrivacy as PrivacySettings, Visibility } from "../types/library";
import { LibraryBoundary, LibraryShell } from "./library-shell";
import { LibraryLoading, LibraryStatus } from "./library-status";

function PrivacyForm({ settings, pending, onSave }: { settings: PrivacySettings; pending: boolean; onSave: (value: Visibility) => void }) {
  const t = useTranslations("Library");
  const [value, setValue] = useState<Visibility>(settings.library);
  return <div className="space-y-5">
    <fieldset disabled={pending} className="space-y-3">
      <legend className="mb-3 font-display text-lg font-semibold">{t("visibility")}</legend>
      {(["PUBLIC", "FRIENDS", "PRIVATE"] as const).map((option) => <label key={option} className={`flex cursor-pointer items-start gap-3 rounded-panel border p-4 ${value === option ? "border-control-border bg-surface-elevated" : "border-border bg-surface"}`}>
        <input type="radio" name="library-visibility" value={option} checked={value === option} onChange={() => setValue(option)} className="mt-1 size-4 shrink-0 accent-primary" /><span><span className="block text-sm font-semibold">{t(`privacy.${option}`)}</span><span className="mt-1 block text-sm leading-6 text-text-secondary">{t(`privacy.${option}Hint`)}</span></span>
      </label>)}
    </fieldset>
    <button type="button" className={buttonClass("primary")} disabled={pending || value === settings.library} onClick={() => onSave(value)}>{t(pending ? "saving" : "save")}</button>
  </div>;
}
function Privacy() {
  const t = useTranslations("Library");
  const resource = useLibrary<PrivacySettings>({ kind: "privacy" });
  const mutation = useLibraryMutation();
  async function save(value: Visibility) {
    if (!resource.data) return;
    await mutation.run({ kind: "privacy", settings: resource.data, library: value });
    // Çakışmada eski nesneyle yeniden gönderim açılmaz; önce yeni version ve bütün alanlar okunur.
    resource.reload();
  }
  return <LibraryShell section="privacy"><div className="max-w-2xl space-y-5">
    <p className="text-sm leading-6 text-text-secondary">{t("privacyHint")}</p>
    {resource.loading && <LibraryLoading />}
    <LibraryStatus error={resource.error} onRetry={resource.reload} />
    <LibraryStatus error={mutation.error} />
    {mutation.saved && <p role="status" className="text-sm text-success">{t("privacySaved")}</p>}
    {resource.data && <PrivacyForm key={JSON.stringify(resource.data)} settings={resource.data} pending={mutation.pending} onSave={save} />}
  </div></LibraryShell>;
}
export function LibraryPrivacy() { return <LibraryBoundary><Privacy /></LibraryBoundary>; }
