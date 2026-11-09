"use client";

import { useTranslations } from "next-intl";
import { usePathname, useSearchParams } from "next/navigation";
import { ApiHttpError } from "@/shared/lib/api/client";
import { Link } from "@/shared/lib/i18n/navigation";
import { buttonClass } from "@/shared/components/ui/button-styles";

export function libraryErrorKey(error: unknown, profile = false): string {
  if (error instanceof ApiHttpError) {
    if (error.code === "CSRF_TOKEN_INVALID") return "csrf";
    if (profile && error.status === 403) return "private";
    if (profile && error.status === 503) return "decision";
    if ([400, 401, 403, 404, 409, 429, 500, 503].includes(error.status)) return String(error.status);
  }
  return "network";
}
export function LibraryStatus({ error, onRetry, profile = false }: { error?: unknown; onRetry?: () => void; profile?: boolean }) {
  const t = useTranslations("Library");
  const pathname = usePathname();
  const params = useSearchParams();
  if (!error) return null;
  const unauthorized = error instanceof ApiHttpError && error.status === 401;
  return <div role="alert" className="space-y-3 rounded-panel border border-border bg-surface p-5">
    <p className="text-sm leading-6">{t(`errors.${libraryErrorKey(error, profile)}`)}</p>
    {unauthorized
      ? <Link href={{ pathname: "/login", query: { next: `${pathname}${params.size ? `?${params}` : ""}` } }} className={buttonClass("primary")}>{t("login")}</Link>
      : onRetry && <button type="button" onClick={onRetry} className={buttonClass("secondary")}>{t("retry")}</button>}
  </div>;
}
export function LibraryLoading() {
  const t = useTranslations("Library");
  return <div role="status" aria-label={t("loading")} className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
    <span className="sr-only">{t("loading")}</span>
    {[0, 1, 2].map((key) => <div key={key} aria-hidden="true" className="overflow-hidden rounded-panel border border-border bg-surface motion-safe:animate-pulse"><div className="aspect-video bg-surface-elevated" /><div className="space-y-3 p-4"><div className="h-5 w-3/4 bg-surface-elevated" /><div className="h-10 bg-surface-elevated" /></div></div>)}
  </div>;
}
