"use client";

import { useId, useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { saveConsents } from "../services/settings-api";
import type { Consents } from "../types/settings.types";
import { settingsErrorKey, type SaveMessage } from "./settings-parts";

/**
 * Rızalarım (LIB-14): kişiselleştirilmiş öneri için favori ve bitirilen oyun verisinin işlenmesi. Varsayılan kapalıdır ve
 * her an geri alınabilir. Değişiklik, kullanıcının bilinçli tıklamasıyla hemen sunucuya yazılır; durum ve zaman yalnız
 * sunucu yanıtı gelince güncellenir. Rıza, koleksiyonun kimlere görünür olduğundan (gizlilik ayarları) bağımsızdır.
 */
export function ConsentsSection({ initial, onRefresh }: { initial: Consents; onRefresh: () => void }) {
  const t = useTranslations("AccountSettings.consents");
  const ta = useTranslations("AccountSettings");
  const te = useTranslations("AccountSettings.errors");
  const format = useFormatter();
  const [current, setCurrent] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<SaveMessage | null>(null);
  const id = useId();

  async function onToggle(next: boolean) {
    if (busy) return;
    setBusy(true);
    setMessage(null);
    try {
      setCurrent(await saveConsents({ ...current, recommendation: next }));
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
      <div className="flex items-start gap-3">
        <input
          id={id}
          type="checkbox"
          checked={current.recommendation}
          disabled={busy}
          aria-describedby={`${id}-hint`}
          onChange={(event) => void onToggle(event.target.checked)}
          className="mt-1 size-5 shrink-0 accent-primary"
        />
        <div className="flex flex-col gap-1">
          <label htmlFor={id} className="text-sm font-medium">
            {t("recommendation.label")}
          </label>
          <p id={`${id}-hint`} className="text-xs text-text-secondary">
            {t("recommendation.hint")}
          </p>
          <p className="text-xs text-text-secondary">
            {current.recommendation ? t("recommendation.on") : t("recommendation.off")}
            {current.recommendationChangedAt
              ? ` · ${t("recommendation.since", {
                  date: format.dateTime(new Date(current.recommendationChangedAt), { dateStyle: "medium" })
                })}`
              : ""}
          </p>
        </div>
      </div>
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
