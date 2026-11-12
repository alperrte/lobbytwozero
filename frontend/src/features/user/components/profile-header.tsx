"use client";

import { useFormatter, useTranslations } from "next-intl";
import type { ReactNode } from "react";
import type { ProfileImage } from "../types/profile.types";
import { ProfileAvatar } from "./profile-avatar";

export const primaryLinkClass =
  "inline-flex min-h-10 items-center justify-center rounded-control bg-primary px-4 text-sm font-semibold text-primary-foreground transition-colors duration-150 hover:bg-secondary";

/** Başlığın ihtiyaç duyduğu alanlar; kendi profili de başkasının açık profili de bu biçime uyar. */
export type HeaderProfile = {
  username: string | null;
  displayName: string | null;
  avatar: ProfileImage | null;
  cover: ProfileImage | null;
  /** Yalnız kendi profilinde gelir; başkasının profilinde üyelik tarihi gösterilmez. */
  memberSince?: string | null;
};

/**
 * Profil başlığı: kapak, avatar, ad, @kullanıcı adı ve (backend gönderdiyse) gerçek üyelik tarihi. Sağdaki eylem (ör. "Profili düzenle")
 * çağıran tarafından verilir.
 */
export function ProfileHeader({ profile, action }: { profile: HeaderProfile; action?: ReactNode }) {
  const t = useTranslations("Profile");
  const format = useFormatter();
  const name = profile.displayName ?? profile.username ?? t("noName");

  return (
    <section aria-labelledby="profile-name" className="edge-light relative rounded-float border border-border bg-surface">
      <div className="relative h-40 overflow-hidden rounded-t-float bg-surface-elevated sm:h-56">
        {profile.cover ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={profile.cover.large} alt="" className="size-full object-cover" referrerPolicy="no-referrer" />
        ) : null}
        <div aria-hidden="true" className="absolute inset-x-0 bottom-0 h-1/2 bg-gradient-to-t from-surface to-transparent" />
      </div>
      <div className="flex flex-col gap-4 px-5 pb-5 sm:flex-row sm:items-end sm:gap-6 sm:px-6">
        <ProfileAvatar
          name={name}
          src={profile.avatar?.large ?? null}
          className="relative -mt-12 size-24 shrink-0 ring-4 ring-surface sm:-mt-16 sm:size-32"
        />
        <div className="min-w-0 flex-1">
          <h1 id="profile-name" className="truncate font-display text-2xl font-semibold sm:text-3xl">
            {name}
          </h1>
          <p className="text-sm text-text-secondary">
            {profile.username ? `@${profile.username}` : t("usernamePending")}
            {profile.memberSince ? (
              <>
                <span aria-hidden="true"> · </span>
                <time dateTime={profile.memberSince}>
                  {t("memberSince", { date: format.dateTime(new Date(profile.memberSince), { dateStyle: "long" }) })}
                </time>
              </>
            ) : null}
          </p>
        </div>
        {action}
      </div>
    </section>
  );
}
