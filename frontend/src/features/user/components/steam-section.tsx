"use client";

import { useId, useState } from "react";
import { useTranslations } from "next-intl";
import { saveSteamVisibility } from "../services/settings-api";
import type { LinkedSteam } from "../types/settings.types";
import { settingsErrorKey, type SaveMessage } from "./settings-parts";

/**
 * Bağlı hesaplar: Steam (USER-08). Bağlama ve kaldırma Auth'tadır; burada yalnız Steam bağlantısının açık profilde gösterilip
 * gösterilmeyeceği seçilir. Varsayılan gizlidir. Bağlı değilse seçim yapılamaz. Değişiklik bilinçli tıklamayla hemen yazılır;
 * durum yalnız sunucu yanıtı gelince güncellenir.
 */
export function SteamSection({ initial, onRefresh }: { initial: LinkedSteam; onRefresh: () => void }) {
  const t = useTranslations("AccountSettings.steam");
  const ta = useTranslations("AccountSettings");
  const te = useTranslations("AccountSettings.errors");
  const [current, setCurrent] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<SaveMessage | null>(null);
  const id = useId();

  async function onToggle(next: boolean) {
    if (busy || !current.linked) return;
    setBusy(true);
    setMessage(null);
    try {
      setCurrent(await saveSteamVisibility(current, next));
      setMessage({ kind: "ok", key: "saved" });
    } catch (error) {
      setMessage({ kind: "error", key: settingsErrorKey(error) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <p className="mb-5 max-w-2xl text-sm text-text-secondary">{t("description")}</p>
      {current.linked ? (
        <div className="flex items-start gap-3">
          <input
            id={id}
            type="checkbox"
            checked={current.showOnProfile}
            disabled={busy}
            aria-describedby={`${id}-hint`}
            onChange={(event) => void onToggle(event.target.checked)}
            className="mt-1 size-5 shrink-0 accent-primary"
          />
          <div className="flex flex-col gap-1">
            <label htmlFor={id} className="text-sm font-medium">
              {t("show.label")}
            </label>
            <p id={`${id}-hint`} className="text-xs text-text-secondary">
              {t("show.hint", { steamId: current.steamId ?? "" })}
            </p>
            <p className="text-xs text-text-secondary">{current.showOnProfile ? t("show.on") : t("show.off")}</p>
          </div>
        </div>
      ) : (
        <p className="rounded-control border border-border bg-surface-elevated px-3 py-3 text-sm text-text-secondary">
          {t("notLinked")}
        </p>
      )}
      <div aria-live="polite" className="mt-3 min-h-6">
        {message ? (
          <p
            role={message.kind === "error" ? "alert" : "status"}
            className={message.kind === "ok" ? "text-sm text-success" : "text-sm text-brand-text"}
          >
            {message.kind === "ok" ? ta("saved") : te(message.key)}
            {message.kind === "error" && message.key === "conflict" ? (
              <button type="button" onClick={onRefresh} className="ms-2 underline">
                {ta("refresh")}
              </button>
            ) : null}
          </p>
        ) : null}
      </div>
    </div>
  );
}
