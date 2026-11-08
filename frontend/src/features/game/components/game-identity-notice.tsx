"use client";

import { useTranslations } from "next-intl";
import { Notice } from "@/shared/components/ui/notice";

/**
 * Dış oyun kimliği taşıma/ayırma bakım alanı. Backend gerçek taşıma ve ayırma isteklerini
 * FOREIGN_REFERENCE_SAFETY_UNVERIFIED ile reddeder; bu nedenle burada çalışan bir form veya düğme yoktur.
 */
export function GameIdentityNotice() {
  const t = useTranslations("Game");
  return <section aria-label={t("identityTitle")} className="space-y-4">
    <h2 className="font-display text-2xl font-semibold">{t("identityTitle")}</h2>
    <Notice title={t("identityBlockedTitle")} headingLevel="h3">
      <p>{t("identityBlockedBody")}</p>
      <ul className="mt-3 list-disc space-y-1 ps-5 text-sm">
        <li>{t("identityPoint1")}</li>
        <li>{t("identityPoint2")}</li>
        <li>{t("identityPoint3")}</li>
      </ul>
    </Notice>
  </section>;
}
