"use client";

import { useTranslations } from "next-intl";
import { ApiHttpError } from "@/shared/lib/api/client";

export const panel = "rounded-panel border border-border bg-surface p-5 sm:p-6";
export const control = "inline-flex min-h-11 items-center justify-center rounded-control border border-control-border bg-surface px-4 py-2 text-sm font-semibold text-text-primary hover:bg-surface-elevated focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-50";
export const select = "min-h-11 max-w-full rounded-control border border-control-border bg-background px-3 text-text-primary";

export function PerformanceLoading() {
  const t = useTranslations("Performance");
  return <p role="status" className={panel}>{t("loading")}</p>;
}

export function PerformanceError({ error, retry }: { error: unknown; retry?: () => void }) {
  const t = useTranslations("Performance");
  if (!error) return null;
  const status = error instanceof ApiHttpError ? error.status : 0;
  const key = [400, 401, 403, 404, 409, 429, 500, 503].includes(status) ? String(status) : status >= 500 ? "500" : "network";
  return <div role="alert" className={panel}>
    <p>{t(`errors.${key}`)}</p>
    {retry && status !== 401 && status !== 403 ? <button className={`${control} mt-3`} onClick={retry}>{t("retry")}</button> : null}
  </div>;
}
