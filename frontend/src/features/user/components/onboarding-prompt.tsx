"use client";

import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import type { Remote } from "../hooks/use-remote";
import type { OnboardingState } from "../services/onboarding-api";
import { primaryLinkClass } from "./profile-header";

/**
 * Profilde "Kurulumu tamamla" kartı. Yalnız sunucu kurulumun bitmediğini açıkça söylediğinde görünür; yükleniyorsa, hata
 * varsa ya da tamamlandıysa hiçbir şey göstermez (bilinmeyen durum için kullanıcıyı yanlış yönlendirmez). Zorla yönlendirme yoktur.
 */
export function OnboardingPrompt({ onboarding }: { onboarding: Remote<OnboardingState> }) {
  const t = useTranslations("Onboarding.prompt");
  const { state } = onboarding;
  if (state.status !== "ready" || state.data.completed) {
    return null;
  }
  return (
    <section className="flex flex-wrap items-center justify-between gap-4 rounded-panel border border-primary/40 bg-surface p-5 sm:p-6">
      <div className="min-w-0 max-w-prose">
        <h2 className="font-display text-base font-semibold">{t("title")}</h2>
        <p className="mt-1 text-sm text-text-secondary">{t("body")}</p>
      </div>
      <Link href="/onboarding" className={primaryLinkClass}>
        {t("action")}
      </Link>
    </section>
  );
}
