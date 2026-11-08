"use client";

import { useTranslations } from "next-intl";
import type { PreviewTabId } from "../preview/preview-data";
import { PreviewMark } from "./preview-mark";

/**
 * Henüz servisi olmayan sekmenin yer tutucusu (Aktivite, Oyunlar, Arkadaşlar). Veri göstermez; yalnız bölümün ne
 * olacağını söyler ve gri iskelet satırlarıyla yerleşimi gösterir.
 */
export function ProfileTabPreview({ tab }: { tab: PreviewTabId }) {
  const t = useTranslations("Profile");
  return (
    <section className="rounded-panel border border-border bg-surface p-5 sm:p-6">
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="font-display text-base font-semibold">{t(`tabs.${tab}`)}</h2>
        <PreviewMark />
      </div>
      <p className="mt-2 max-w-prose text-sm text-text-secondary">{t(`tabPreview.${tab}`)}</p>
      <ul aria-hidden="true" className="mt-5 flex flex-col gap-3">
        {[0, 1, 2].map((row) => (
          <li key={row} className="flex items-center gap-3 rounded-control border border-border bg-surface-elevated px-3 py-3">
            <span className="size-9 shrink-0 rounded-full bg-border" />
            <span className="flex flex-1 flex-col gap-2">
              <span className="h-3 w-1/3 rounded-control bg-border" />
              <span className="h-3 w-2/3 rounded-control bg-border" />
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
