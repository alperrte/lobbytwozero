"use client";

import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import type { Remote } from "../hooks/use-remote";
import type { OnboardingState } from "../services/onboarding-api";
import type { MyProfile, SteamLink } from "../types/profile.types";
import { editHref } from "./edit-sections";
import { primaryLinkClass } from "./profile-header";

/** Her adımın götürdüğü yer: düzenleme sayfasındaki bölüm (Steam bağlantısı ayarlardadır). */
const STEP_HREF = {
  avatar: editHref("images"),
  cover: editHref("images"),
  bio: editHref("info"),
  country: editHref("info"),
  playerTypes: editHref("prefs"),
  genres: editHref("prefs"),
  platforms: editHref("prefs"),
  steam: "/settings"
} as const;

const STEPS = ["avatar", "cover", "bio", "country", "playerTypes", "genres", "platforms", "steam"] as const;
type Step = (typeof STEPS)[number];

/** Hangi adımların tamamlandığı yalnız gerçek profil verisinden hesaplanır (sahte ilerleme yok). `steam: null` = bilinmiyor, sayılmaz. */
export function completedSteps(profile: MyProfile, steam: SteamLink | null): Record<Step, boolean> {
  return {
    avatar: profile.avatar !== null,
    cover: profile.cover !== null,
    bio: profile.bio !== null,
    country: profile.country !== null,
    playerTypes: profile.playerTypes.length > 0,
    genres: profile.favoriteGenres.length > 0,
    platforms: profile.favoritePlatforms.length > 0,
    steam: steam?.linked === true
  };
}

/**
 * Profil tamamlama kartı: yüzde, adım listesi ve eksikler için düzenleme bağlantısı. Sunucu "kurulum tamamlanmadı" diyorsa
 * kurulum çağrısı da aynı kartta çıkar (ayrı bant yok). Yükleniyor/hata/tamamlandı hâlinde kurulum satırı gösterilmez;
 * bilinmeyen durumda kullanıcı yanlış yönlendirilmez. Hem adımlar tamam hem kurulum bitmişse kart gösterilmez.
 */
export function ProfileCompletion({
  profile,
  steam,
  onboarding
}: {
  profile: MyProfile;
  steam: SteamLink | null;
  onboarding?: Remote<OnboardingState>;
}) {
  const t = useTranslations("Profile.completion");
  const to = useTranslations("Onboarding.prompt");
  const done = completedSteps(profile, steam);
  const total = STEPS.length;
  const count = STEPS.filter((step) => done[step]).length;
  const setupPending = onboarding?.state.status === "ready" && !onboarding.state.data.completed;
  const stepsDone = count === total;
  if (stepsDone && !setupPending) {
    return null;
  }
  const percent = Math.round((count / total) * 100);

  return (
    <section className="rounded-panel border border-border bg-surface p-5 sm:p-6">
      <div className="mb-3 flex items-baseline justify-between gap-3">
        <h2 className="font-display text-base font-semibold">{t("title")}</h2>
        {stepsDone ? null : <span className="font-display text-lg font-semibold">{percent}%</span>}
      </div>
      {setupPending ? (
        <div className="mb-4 rounded-control border border-primary/40 bg-surface-elevated p-4">
          <p className="text-sm font-semibold">{to("title")}</p>
          <p className="mt-1 text-sm text-text-secondary">{to("body")}</p>
          <Link href="/onboarding" className={cn(primaryLinkClass, "mt-3")}>
            {to("action")}
          </Link>
        </div>
      ) : null}
      {stepsDone ? null : (
        <>
          <div
            role="progressbar"
            aria-label={t("title")}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={percent}
            className="h-2 overflow-hidden rounded-full border border-border bg-surface-elevated"
          >
            <div className="h-full rounded-full bg-primary transition-[width] duration-300" style={{ width: `${percent}%` }} />
          </div>
          <ul className="mt-4 flex flex-col gap-2">
            {STEPS.map((step) => (
              <li key={step} className="flex items-center gap-2 text-sm">
                <span
                  aria-hidden="true"
                  className={cn(
                    "flex size-4 shrink-0 items-center justify-center rounded-full border text-[10px] leading-none",
                    done[step] ? "border-success bg-success text-primary-foreground" : "border-border"
                  )}
                >
                  {done[step] ? "✓" : ""}
                </span>
                {done[step] ? (
                  <span className="text-text-secondary line-through">{t(`steps.${step}`)}</span>
                ) : (
                  <Link href={STEP_HREF[step]} className="text-text-primary hover:text-brand-text hover:underline">
                    {t(`steps.${step}`)}
                  </Link>
                )}
                {done[step] ? <span className="sr-only">{t("done")}</span> : null}
              </li>
            ))}
          </ul>
          <Link
            href="/profile/edit"
            className="mt-4 inline-flex min-h-10 items-center justify-center rounded-control border border-control-border px-4 text-sm font-semibold transition-colors duration-150 hover:bg-surface-elevated"
          >
            {t("action")}
          </Link>
        </>
      )}
    </section>
  );
}
