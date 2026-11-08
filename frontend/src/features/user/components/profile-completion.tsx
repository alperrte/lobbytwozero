"use client";

import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import type { MyProfile, SteamLink } from "../types/profile.types";

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

/** Profil tamamlama kartı: yüzde, adım listesi ve eksikler için düzenleme bağlantısı. Hepsi tamamsa kart gösterilmez. */
export function ProfileCompletion({ profile, steam }: { profile: MyProfile; steam: SteamLink | null }) {
  const t = useTranslations("Profile.completion");
  const done = completedSteps(profile, steam);
  const total = STEPS.length;
  const count = STEPS.filter((step) => done[step]).length;
  if (count === total) {
    return null;
  }
  const percent = Math.round((count / total) * 100);

  return (
    <section className="rounded-panel border border-border bg-surface p-5 sm:p-6">
      <div className="mb-3 flex items-baseline justify-between gap-3">
        <h2 className="font-display text-base font-semibold">{t("title")}</h2>
        <span className="font-display text-lg font-semibold">{percent}%</span>
      </div>
      <div
        role="progressbar"
        aria-label={t("title")}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        className="h-2 overflow-hidden rounded-full bg-surface-elevated"
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
            <span className={cn(done[step] ? "text-text-secondary line-through" : "text-text-primary")}>{t(`steps.${step}`)}</span>
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
    </section>
  );
}
