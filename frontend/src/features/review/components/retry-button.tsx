"use client";

import { useEffect } from "react";
import { useTranslations } from "next-intl";
import { useRetryCountdown } from "../hooks/use-retry-countdown";
import { retryAfterOf } from "../services/review-errors";
import { focusRing } from "./review-ui";

/**
 * Oyun kartı (Game `batch`) yüklemesi için "tekrar dene" bağlantısı. Game `429` + `Retry-After` döndürürse düğme süre
 * dolana kadar kapalıdır ve kalan saniyeyi söyler; otomatik yeniden deneme YOKTUR. Kart verisi gelmese de inceleme/puan
 * verisi gizlenmez (çağıran bileşen yalnız başlık yerine tarafsız ifade gösterir).
 */
export function RetryButton({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  const t = useTranslations("Review");
  const countdown = useRetryCountdown();
  const seconds = retryAfterOf(error);
  const { start } = countdown;
  useEffect(() => {
    if (seconds) start(seconds);
  }, [error, seconds, start]);
  const waiting = countdown.remaining > 0;
  return (
    <button type="button" onClick={onRetry} disabled={waiting} aria-disabled={waiting} className={`rounded-control underline underline-offset-2 disabled:cursor-not-allowed disabled:no-underline disabled:opacity-60 ${focusRing}`}>
      {waiting ? t("retryWait", { seconds: countdown.remaining }) : t("retry")}
    </button>
  );
}
