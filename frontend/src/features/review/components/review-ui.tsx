"use client";

import type { ReactNode } from "react";
import Image from "next/image";
import { useTranslations } from "next-intl";
import { usePathname, useSearchParams } from "next/navigation";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { describeReviewError } from "../services/review-errors";
import type { ReviewErrorContext } from "../types/review";

/** Odak halkası: tüm etkileşimli Review kontrolleri aynı belirgin odak göstergesini kullanır (WCAG 2.2 AA). */
export const focusRing = "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus";

/** Bağlantı veya düğme görünümü: en az 44 px yükseklik (dokunmatik hedef) ve ortak odak halkası. */
export function actionClass(variant: "primary" | "secondary" | "ghost" = "secondary"): string {
  return buttonClass(variant, "md", `min-h-11 ${focusRing}`);
}

export function Panel({ title, titleId, description, actions, children, className }: { title?: string; titleId?: string; description?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section aria-labelledby={title ? titleId : undefined} className={cn("min-w-0 rounded-panel border border-border bg-surface p-5 sm:p-6", className)}>
      {title || actions ? (
        <header className="mb-4 flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0 space-y-1">
            {title ? <h2 id={titleId} className="font-display text-lg font-semibold">{title}</h2> : null}
            {description ? <p className="max-w-prose text-sm leading-6 text-text-secondary">{description}</p> : null}
          </div>
          {actions}
        </header>
      ) : null}
      {children}
    </section>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden="true" className={cn("rounded-control bg-surface-elevated motion-safe:animate-pulse", className)} />;
}

/** Yükleniyor durumu: ekran okuyucuya tek duyuru, görsel olarak yer tutucu bloklar. */
export function ReviewLoading({ className }: { className?: string }) {
  const t = useTranslations("Review");
  return (
    <div role="status" aria-label={t("loading")} className={cn("space-y-4", className)}>
      <span className="sr-only">{t("loading")}</span>
      <Skeleton className="h-8 w-1/2" />
      <Skeleton className="h-32" />
      <Skeleton className="h-32" />
    </div>
  );
}

export function EmptyState({ title, hint, action }: { title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="space-y-3 rounded-panel border border-dashed border-border bg-surface px-5 py-8 text-center">
      <p className="font-display text-base font-semibold">{title}</p>
      {hint ? <p className="mx-auto max-w-prose text-sm leading-6 text-text-secondary">{hint}</p> : null}
      {action ? <div className="flex justify-center">{action}</div> : null}
    </div>
  );
}

/**
 * Hata kutusu. Sunucu metni gösterilmez; mesaj HTTP durumuna ve işlem bağlamına göre seçilir. 401'de giriş bağlantısı
 * (girişten sonra aynı sayfaya dönüş), sürüm çakışmasında "güncel hali yükle", güvenli okumalarda "tekrar dene" sunulur.
 */
export function ReviewAlert({ error, context = "read", onRetry, onReload, className }: { error: unknown; context?: ReviewErrorContext; onRetry?: () => void; onReload?: () => void; className?: string }) {
  const t = useTranslations("Review");
  const pathname = usePathname();
  const params = useSearchParams();
  if (!error) return null;
  const info = describeReviewError(error, context);
  const next = `${pathname}${params.size ? `?${params}` : ""}`;
  return (
    <div role="alert" className={cn("space-y-3 rounded-panel border border-primary/40 bg-primary/5 p-4", className)}>
      <p className="text-sm leading-6">{t(`errors.${info.key}`, { seconds: info.retryAfterSeconds ?? 0 })}</p>
      <div className="flex flex-wrap gap-2">
        {info.needsLogin ? (
          <Link href={{ pathname: "/login", query: { next } }} className={buttonClass("primary", "sm", focusRing)}>{t("login")}</Link>
        ) : null}
        {info.conflict && onReload ? <button type="button" onClick={onReload} className={buttonClass("secondary", "sm", focusRing)}>{t("reloadLatest")}</button> : null}
        {!info.conflict && !info.needsLogin && onRetry ? <button type="button" onClick={onRetry} className={buttonClass("secondary", "sm", focusRing)}>{t("retry")}</button> : null}
      </div>
    </div>
  );
}

/** Kapak görseli: yalnız https; yoksa/açılmazsa nötr yer tutucu (stok görsel kullanılmaz). */
export function ReviewCover({ url, title, className }: { url: string | null; title: string; className?: string }) {
  const t = useTranslations("Review");
  return (
    <div className={cn("relative aspect-video overflow-hidden bg-surface-elevated", className)}>
      {url ? (
        <Image unoptimized src={url} alt={t("coverAlt", { title })} fill sizes="(min-width: 1024px) 25vw, (min-width: 640px) 50vw, 100vw" referrerPolicy="no-referrer" loading="lazy" className="object-cover" />
      ) : (
        <div data-testid="cover-placeholder" aria-hidden="true" className="flex h-full w-full items-center justify-center text-text-secondary">
          <Icon name="gamepad" width={32} height={32} />
        </div>
      )}
    </div>
  );
}

export function Pagination({ page, size, totalElements, onChange, label }: { page: number; size: number; totalElements: number; onChange: (page: number) => void; label: string }) {
  const t = useTranslations("Review");
  const lastPage = Math.max(0, Math.min(10000, Math.ceil(totalElements / size) - 1));
  if (totalElements <= size && page === 0) return null;
  return (
    <nav aria-label={label} className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4">
      <button type="button" disabled={page <= 0} onClick={() => onChange(page - 1)} className={buttonClass("secondary", "md", cn("min-h-11", focusRing))}>{t("pagination.previous")}</button>
      <span className="text-sm tabular-nums text-text-secondary" aria-live="polite">{t("pagination.page", { page: page + 1, pages: lastPage + 1 })}</span>
      <button type="button" disabled={page >= lastPage} onClick={() => onChange(page + 1)} className={buttonClass("secondary", "md", cn("min-h-11", focusRing))}>{t("pagination.next")}</button>
    </nav>
  );
}

export function Stat({ label, value, hint }: { label: string; value: ReactNode; hint?: string }) {
  return (
    <div className="min-w-0 space-y-1 rounded-panel border border-border bg-surface p-4">
      <dt className="text-xs font-medium uppercase tracking-wide text-text-secondary">{label}</dt>
      <dd className="font-display text-2xl font-semibold tabular-nums [overflow-wrap:anywhere]">{value}</dd>
      {hint ? <dd className="text-xs leading-5 text-text-secondary">{hint}</dd> : null}
    </div>
  );
}
