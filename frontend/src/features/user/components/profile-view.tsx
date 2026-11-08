"use client";

import { useId, useState } from "react";
import { useTranslations } from "next-intl";
import { useRequireSession } from "@/features/auth";
import { PageContainer } from "@/shared/components/layout/page-container";
import { Link } from "@/shared/lib/i18n/navigation";
import { useMyProfile } from "../hooks/use-my-profile";
import type { PreviewTabId } from "../preview/preview-data";
import type { MyProfile, ProfileStats, SteamLink } from "../types/profile.types";
import { ProfileHeader } from "./profile-header";
import { ProfileOverview } from "./profile-overview";
import { ProfileStateNotice } from "./profile-state-notice";
import { ProfileStatsStrip } from "./profile-stats";
import { ProfileTabPreview } from "./profile-tab-preview";
import { panelId, ProfileTabs, profileTabs, tabId, type ProfileTabId } from "./profile-tabs";

/** Kendi profilin: başlık, istatistik şeridi ve sekmeler. Veri `GET /api/v1/user/me` ve `GET /api/v1/user/profiles/{id}`'den gelir. */
export function ProfileView() {
  const session = useRequireSession();
  const accountId = session.status === "authenticated" ? session.me.accountId : null;
  const { state, retry } = useMyProfile(session.status === "authenticated", { steam: true, statsFor: accountId });
  const t = useTranslations("Profile");

  if (session.status !== "authenticated" || state.status === "loading") {
    return (
      <PageContainer>
        <ProfileSkeleton label={t("loading")} />
      </PageContainer>
    );
  }
  if (state.status === "error") {
    return (
      <PageContainer>
        <ProfileStateNotice
          title={t("errorTitle")}
          body={t("errorBody")}
          actionLabel={t("retry")}
          onAction={retry}
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <ProfileContent profile={state.profile} steam={state.steam} stats={state.stats} />
    </PageContainer>
  );
}

function ProfileContent({
  profile,
  steam,
  stats
}: {
  profile: MyProfile;
  steam: SteamLink | null;
  stats: ProfileStats | null;
}) {
  const prefix = useId();
  const [active, setActive] = useState<ProfileTabId>("overview");

  return (
    <div className="flex flex-col gap-6">
      <ProfileHeader profile={profile} />
      <ProfileStatsStrip stats={stats} />
      <div className="flex flex-col gap-6">
        <ProfileTabs active={active} onChange={setActive} idPrefix={prefix} />
        {profileTabs.map((tab) => (
          <div
            key={tab}
            role="tabpanel"
            id={panelId(prefix, tab)}
            aria-labelledby={tabId(prefix, tab)}
            hidden={tab !== active}
          >
            {tab === active ? <TabBody tab={tab} profile={profile} steam={steam} /> : null}
          </div>
        ))}
      </div>
    </div>
  );
}

function TabBody({ tab, profile, steam }: { tab: ProfileTabId; profile: MyProfile; steam: SteamLink | null }) {
  if (tab === "overview") {
    return <ProfileOverview profile={profile} steam={steam} />;
  }
  if (tab === "hardware") {
    return <HardwarePanel />;
  }
  return <ProfileTabPreview tab={tab as PreviewTabId} />;
}

/** Donanım gerçek bir sayfadır (Hardware servisi): sekme yalnız oraya yönlendirir. */
function HardwarePanel() {
  const t = useTranslations("Profile.hardware");
  return (
    <section className="rounded-panel border border-border bg-surface p-5 sm:p-6">
      <h2 className="font-display text-base font-semibold">{t("title")}</h2>
      <p className="mt-2 max-w-prose text-sm text-text-secondary">{t("body")}</p>
      <Link
        href="/profile/computers"
        className="mt-4 inline-flex min-h-10 items-center justify-center rounded-control border border-control-border px-4 text-sm font-semibold transition-colors duration-150 hover:bg-surface-elevated"
      >
        {t("open")}
      </Link>
    </section>
  );
}

function ProfileSkeleton({ label }: { label: string }) {
  return (
    <div role="status" aria-live="polite" className="flex flex-col gap-6">
      <span className="sr-only">{label}</span>
      <div className="animate-pulse rounded-float border border-border bg-surface">
        <div className="h-40 rounded-t-float bg-surface-elevated sm:h-56" />
        <div className="flex flex-col gap-3 px-5 py-6 sm:px-6">
          <div className="h-6 w-48 rounded-control bg-surface-elevated" />
          <div className="h-4 w-32 rounded-control bg-surface-elevated" />
          <div className="h-4 w-full max-w-prose rounded-control bg-surface-elevated" />
        </div>
      </div>
    </div>
  );
}
