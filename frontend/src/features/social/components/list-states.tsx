"use client";

import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon, type IconName } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import { socialErrorKey } from "../services/social-errors";

/** Yükleniyor iskeleti: gerçek yerleşimi taklit eder (kart ızgarası ya da satır listesi). */
export function ListSkeleton({ variant, count = 4 }: { variant: "cards" | "rows"; count?: number }) {
  const t = useTranslations("Social.states");
  return (
    <div role="status" aria-live="polite">
      <span className="sr-only">{t("loading")}</span>
      <div
        aria-hidden="true"
        className={cn(variant === "cards" ? "grid gap-4 xl:grid-cols-2" : "space-y-3")}
      >
        {Array.from({ length: count }, (_, index) => (
          <div
            key={index}
            className={cn(
              "flex items-center gap-4 rounded-panel border border-border bg-surface p-4",
              variant === "cards" ? "h-40" : "h-24"
            )}
          >
            <span className="size-14 shrink-0 animate-pulse rounded-full bg-surface-elevated motion-reduce:animate-none" />
            <span className="flex-1 space-y-2.5">
              <span className="block h-4 w-2/5 animate-pulse rounded-control bg-surface-elevated motion-reduce:animate-none" />
              <span className="block h-3 w-1/4 animate-pulse rounded-control bg-surface-elevated motion-reduce:animate-none" />
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

export function EmptyState({
  icon,
  title,
  body,
  action
}: {
  icon: IconName;
  title: string;
  body: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center rounded-panel border border-dashed border-border bg-surface px-6 py-12 text-center">
      <span className="inline-flex size-14 items-center justify-center rounded-full bg-surface-elevated text-text-secondary">
        <Icon name={icon} className="size-7" />
      </span>
      <h3 className="mt-4 font-display text-lg font-semibold text-text-primary">{title}</h3>
      <p className="mt-1.5 max-w-sm text-sm text-text-secondary">{body}</p>
      {action ? <div className="mt-5">{action}</div> : null}
    </div>
  );
}

/** Liste okunamadı: duruma özgü neden ve güvenli "Tekrar dene". */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  const t = useTranslations("Social");
  return (
    <div role="alert" className="rounded-panel border border-primary/50 bg-surface px-6 py-10 text-center">
      <Icon name="info" className="mx-auto size-8 text-brand-text" />
      <h3 className="mt-3 font-display text-lg font-semibold">{t("states.errorTitle")}</h3>
      <p className="mt-1.5 text-sm text-text-secondary">{t(`errors.${socialErrorKey(error)}`)}</p>
      <button type="button" onClick={onRetry} className={buttonClass("secondary", "md", "mt-5")}>
        <Icon name="refresh" className="size-4" />
        {t("states.retry")}
      </button>
    </div>
  );
}

/** "Daha fazla yükle" (imleçli sayfalama; web-rules §7.1 erişilebilir yükleme). */
export function LoadMore({
  label,
  loading,
  failed,
  onLoad
}: {
  label: string;
  loading: boolean;
  failed: boolean;
  onLoad: () => void;
}) {
  const t = useTranslations("Social.states");
  return (
    <div className="flex flex-col items-center gap-2">
      {failed ? (
        <p role="alert" className="text-sm text-brand-text">
          {t("loadMoreFailed")}
        </p>
      ) : null}
      <button
        type="button"
        onClick={onLoad}
        disabled={loading}
        aria-busy={loading || undefined}
        className={buttonClass("secondary")}
      >
        {loading ? t("loading") : label}
      </button>
    </div>
  );
}
