"use client";

import { useFormatter, useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { cn } from "@/shared/utils/cn";
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

/** Kapak görseli yokken görünen varsayılan kapak: kırmızı ışık hüzmesi ve ince ızgara (görsel yüklenmeden de boş durmaz). */
function DefaultCover() {
  return (
    <div aria-hidden="true" data-testid="default-cover" className="absolute inset-0">
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_20%_0%,color-mix(in_srgb,var(--color-primary)_35%,transparent),transparent_60%)]" />
      <div className="absolute inset-0 bg-[linear-gradient(to_right,var(--color-border)_1px,transparent_1px),linear-gradient(to_bottom,var(--color-border)_1px,transparent_1px)] bg-[size:32px_32px] opacity-40 [mask-image:linear-gradient(to_bottom,black,transparent)]" />
    </div>
  );
}

/**
 * Profil başlığı: kapak, avatar, ad, @kullanıcı adı ve (backend gönderdiyse) gerçek üyelik tarihi. Sağdaki eylem (ör. "Profili düzenle")
 * çağıran tarafından verilir.
 */
export function ProfileHeader({
  profile,
  action,
  compact = false
}: {
  profile: HeaderProfile;
  action?: ReactNode;
  /** Düzenleme önizlemesi gibi küçük yerler için: kapak, avatar ve ad daha küçük çizilir. */
  compact?: boolean;
}) {
  const t = useTranslations("Profile");
  const format = useFormatter();
  const name = profile.displayName ?? profile.username ?? t("noName");

  return (
    <section aria-labelledby="profile-name" className="edge-light relative rounded-float border border-border bg-surface">
      <div className={cn("relative overflow-hidden rounded-t-float bg-surface-elevated", compact ? "h-20 sm:h-24" : "h-40 sm:h-56")}>
        {profile.cover ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={profile.cover.large} alt="" className="size-full object-cover" referrerPolicy="no-referrer" />
        ) : (
          <DefaultCover />
        )}
        <div aria-hidden="true" className="absolute inset-x-0 bottom-0 h-1/2 bg-gradient-to-t from-surface to-transparent" />
      </div>
      <div className={cn("flex flex-col px-5 sm:flex-row sm:items-end sm:px-6", compact ? "gap-3 pb-4 sm:gap-4" : "gap-4 pb-5 sm:gap-6")}>
        <ProfileAvatar
          name={name}
          src={profile.avatar?.large ?? null}
          className={cn(
            "relative shrink-0 ring-4 ring-surface",
            compact ? "-mt-8 size-16 sm:-mt-10 sm:size-20" : "-mt-12 size-24 sm:-mt-16 sm:size-32"
          )}
        />
        <div className="min-w-0 flex-1">
          <h1
            id="profile-name"
            className={cn("truncate font-display font-semibold", compact ? "text-xl" : "text-2xl sm:text-3xl")}
          >
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
