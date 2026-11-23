"use client";

import { useEffect, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon, type IconName } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import { errorCode, errorStatus, retryAfter } from "../services/recommendation-errors";

export const panelClass = "rounded-panel border border-border bg-surface";

/** Bölüm başlığı: kısa kırmızı çizgi + başlık + isteğe bağlı açıklama ve sağda eylem. */
export function SectionHeading({ id, title, description, action, level = "h2" }: { id?: string; title: string; description?: string; action?: ReactNode; level?: "h2" | "h3" }) {
  const Heading = level;
  return <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
    <div className="min-w-0 max-w-2xl">
      <Heading id={id} className="font-display text-xl font-semibold text-text-primary sm:text-2xl">{title}</Heading>
      {description ? <p className="mt-1 text-sm leading-6 text-text-secondary">{description}</p> : null}
    </div>
    {action ? <div className="flex shrink-0 flex-wrap items-center gap-2">{action}</div> : null}
  </div>;
}

/** Kart oranları sabit iskelet (düzen kaymasını önler). `label` ekran okuyucuya yüklendiğini söyler. */
export function RecommendationSkeleton({ count = 3, variant = "card" }: { count?: number; variant?: "card" | "row" }) {
  const t = useTranslations("Recommendation.common");
  return <div role="status" aria-live="polite">
    <span className="sr-only">{t("loading")}</span>
    <div aria-hidden="true" className={cn("motion-safe:animate-pulse", variant === "card" ? "grid gap-4 sm:grid-cols-2 xl:grid-cols-3" : "space-y-3")}>
      {Array.from({ length: count }, (_, index) => variant === "card"
        ? <div key={index} className={cn(panelClass, "overflow-hidden")}><div className="aspect-video bg-surface-elevated" /><div className="space-y-2 p-4"><div className="h-4 w-2/3 rounded bg-surface-elevated" /><div className="h-3 w-1/2 rounded bg-surface-elevated" /></div></div>
        : <div key={index} className={cn(panelClass, "h-20")} />)}
    </div>
  </div>;
}

export function RecommendationEmpty({ icon = "sparkles", title, description, children }: { icon?: IconName; title: string; description: string; children?: ReactNode }) {
  return <div className={cn(panelClass, "flex flex-col items-center px-6 py-10 text-center")}>
    <span className="mb-4 flex size-12 items-center justify-center rounded-panel border border-border bg-surface-elevated">
      <Icon name={icon} className="size-6 text-text-secondary" />
    </span>
    <h3 className="font-display text-lg font-semibold">{title}</h3>
    <p className="mt-2 max-w-md text-sm leading-6 text-text-secondary">{description}</p>
    {children ? <div className="mt-5 flex flex-wrap justify-center gap-3">{children}</div> : null}
  </div>;
}

const KNOWN_STATUSES = [400, 401, 403, 404, 409, 429, 500, 502, 503] as const;
/** Duruma bağlı başlık yerine daha açıklayıcı metni olan backend kodları. */
const KNOWN_CODES = ["HISTORY_LIMIT_REACHED", "RECOMMENDATION_NOT_FOUND", "GAME_UNAVAILABLE", "SOURCE_NOT_FOUND", "PROFILE_WINDOW_EXCEEDED", "INVALID_RESPONSE", "NETWORK", "SOURCE_ACCESS_DENIED", "FEEDBACK_VERSION_CONFLICT", "CONSENT_REQUIRED"] as const;

export function errorMessageKey(error: unknown): string {
  const code = errorCode(error);
  if (code && (KNOWN_CODES as readonly string[]).includes(code)) return `codes.${code}`;
  if (code?.startsWith("ASSISTANT_")) return "codes.ASSISTANT_UNAVAILABLE";
  const status = errorStatus(error);
  return (KNOWN_STATUSES as readonly number[]).includes(status) ? `status.${status}` : "status.network";
}

/**
 * Bölüm bazlı hata kutusu: hub'daki bir bölüm düşerse diğerleri çalışmaya devam eder. 429/503'te sunucunun
 * `Retry-After` süresi geri sayılır ve "Tekrar dene" bu süre dolana kadar pasif kalır.
 */
export function RecommendationError({ error, retry, compact = false }: { error: unknown; retry?: () => void; compact?: boolean }) {
  const t = useTranslations("Recommendation.errors");
  const common = useTranslations("Recommendation.common");
  const wait = retryAfter(error);
  const [elapsed, setElapsed] = useState<{ error: unknown; seconds: number }>({ error, seconds: 0 });
  const seconds = elapsed.error === error ? elapsed.seconds : 0;
  const remaining = wait ? Math.max(0, wait - seconds) : 0;
  useEffect(() => {
    if (remaining <= 0) return;
    const timer = window.setTimeout(() => setElapsed({ error, seconds: seconds + 1 }), 1000);
    return () => window.clearTimeout(timer);
  }, [remaining, error, seconds]);
  if (!error) return null;
  const key = errorMessageKey(error);
  return <div role="alert" className={cn(panelClass, "border-s-2 border-s-warning", compact ? "p-4" : "p-5")}>
    <p className="font-display font-semibold">{t(`${key}.title`)}</p>
    <p className="mt-1 text-sm leading-6 text-text-secondary">{t(`${key}.description`)}</p>
    {remaining > 0 ? <p className="mt-2 text-sm text-text-secondary" aria-live="polite">{common("retryIn", { seconds: remaining })}</p> : null}
    {retry ? <button type="button" className={buttonClass("secondary", "sm", "mt-4")} disabled={remaining > 0} onClick={retry}>{common("retry")}</button> : null}
  </div>;
}

/** Inline hata (eylem düğmesinin yanında, kısa). */
export function InlineError({ error }: { error: unknown }) {
  const t = useTranslations("Recommendation.errors");
  if (!error) return null;
  return <p role="alert" className="text-sm text-brand-text">{t(`${errorMessageKey(error)}.title`)}</p>;
}

/** Backend `limitations` kodları → kullanıcıya dürüst açıklama. Bilinmeyen kod genel metinle gösterilir. */
export function LimitationsNotice({ codes }: { codes: readonly string[] }) {
  const t = useTranslations("Recommendation.limitations");
  if (!codes.length) return null;
  const unique = [...new Set(codes)];
  return <div className="rounded-panel border border-border bg-surface-elevated/60 p-4 text-sm">
    <p className="flex items-center gap-2 font-semibold text-text-primary"><Icon name="info" className="size-4 shrink-0" />{t("title")}</p>
    <ul className="mt-2 list-disc space-y-1 ps-6 leading-6 text-text-secondary">
      {unique.map((code) => <li key={code}>{t.has(code) ? t(code) : t("UNKNOWN")}</li>)}
    </ul>
  </div>;
}

/** Ortak ilgi/eşleşen tür gibi kısa etiket listesi. Boşsa hiçbir şey çizmez. */
export function ChipList({ label, items }: { label: string; items: readonly string[] }) {
  if (!items.length) return null;
  return <div className="min-w-0">
    <p className="text-xs font-medium text-text-secondary">{label}</p>
    <ul className="mt-1.5 flex flex-wrap gap-1.5" aria-label={label}>
      {items.map((item) => <li key={item} className="max-w-full truncate rounded-full border border-border bg-surface-elevated px-2.5 py-0.5 text-xs text-text-primary">{item}</li>)}
    </ul>
  </div>;
}

/** Sonuç sırası: skor yüzde/olasılık olmadığından yalnız sıra gösterilir. */
export function RankBadge({ rank }: { rank: number }) {
  const t = useTranslations("Recommendation.common");
  return <span className="inline-flex min-w-9 items-center justify-center rounded-control bg-background/85 px-2 py-1 font-display text-sm font-bold text-text-primary ring-1 ring-border" aria-label={t("rank", { rank })}>
    <span aria-hidden="true">#{rank}</span>
  </span>;
}
