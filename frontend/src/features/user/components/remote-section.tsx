"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import type { Remote } from "../hooks/use-remote";

export const sectionLinkClass =
  "inline-flex min-h-10 items-center justify-center rounded-control border border-control-border px-4 text-sm font-semibold transition-colors duration-150 hover:bg-surface-elevated";

/** Profil sekmelerindeki bölüm kartı: başlık, isteğe bağlı sağ eylem ve içerik. */
export function ProfileSection({
  title,
  action,
  children
}: {
  title: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="rounded-panel border border-border bg-surface p-5 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-display text-base font-semibold">{title}</h2>
        {action}
      </div>
      <div className="mt-4">{children}</div>
    </section>
  );
}

/**
 * Uzaktan okunan verinin üç hâli: yüklenirken iskelet, hata olunca tekrar dene, hazırsa içerik. Bir bölümün hatası profilin
 * geri kalanını bozmaz; her bölüm kendi hatasını gösterir.
 */
export function RemoteBody<T>({ remote, children }: { remote: Remote<T>; children: (data: T) => ReactNode }) {
  const t = useTranslations("Profile.remote");
  const { state, retry } = remote;

  if (state.status === "idle" || state.status === "loading") {
    return (
      <div role="status" aria-live="polite" className="flex animate-pulse flex-col gap-3">
        <span className="sr-only">{t("loading")}</span>
        <div className="h-4 w-1/3 rounded-control bg-surface-elevated" />
        <div className="h-4 w-2/3 rounded-control bg-surface-elevated" />
        <div className="h-4 w-1/2 rounded-control bg-surface-elevated" />
      </div>
    );
  }
  if (state.status === "error") {
    return (
      <div role="alert" className="flex flex-wrap items-center gap-3">
        <p className="text-sm text-text-secondary">{t("error")}</p>
        <button type="button" onClick={retry} className={sectionLinkClass}>
          {t("retry")}
        </button>
      </div>
    );
  }
  return <>{children(state.data)}</>;
}

/** Sayı kutusu (etiket + değer). */
export function StatBox({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-control border border-border bg-surface-elevated px-4 py-3">
      <dt className="text-xs text-text-secondary">{label}</dt>
      <dd className="mt-1 font-display text-lg font-semibold text-text-primary">{value}</dd>
    </div>
  );
}
