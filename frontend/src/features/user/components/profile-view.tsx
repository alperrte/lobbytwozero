"use client";

import { useId, useState } from "react";
import { useTranslations } from "next-intl";
import { useRequireSession } from "@/features/auth";
import { PageContainer } from "@/shared/components/layout/page-container";
import { useMyProfile } from "../hooks/use-my-profile";
import { useRemote, type Remote } from "../hooks/use-remote";
import { getFriends, getHardwareSummary, getLibraryStats } from "../services/activity-api";
import { getOnboarding } from "../services/onboarding-api";
import type { FriendsPage, HardwareSummary, LibraryStats } from "../types/activity.types";
import type { MyProfile, ProfileStats, SteamLink } from "../types/profile.types";
import { OnboardingPrompt } from "./onboarding-prompt";
import { ProfileActivity } from "./profile-activity";
import { ProfileFriends } from "./profile-friends";
import { ProfileGames } from "./profile-games";
import { ProfileHardware } from "./profile-hardware";
import { Link } from "@/shared/lib/i18n/navigation";
import { primaryLinkClass, ProfileHeader } from "./profile-header";
import { ProfileOverview } from "./profile-overview";
import { ProfileStateNotice } from "./profile-state-notice";
import { ProfileStatsStrip } from "./profile-stats";
import { panelId, ProfileTabs, profileTabs, tabId, type ProfileTabId } from "./profile-tabs";

/**
 * Kendi profilin: başlık, istatistik şeridi ve sekmeler. Profil `GET /user/me`'den gelir. Arkadaş, oyun ve bilgisayar sayıları
 * ile sekme içerikleri Social, Library, Community, Hardware ve Performance servislerinden okunur; her bölüm kendi hatasını
 * gösterir ve diğerlerini bozmaz. Yalnız rütbe, rozet, imza ve durum mesajı henüz önizlemedir.
 */
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
      <ProfileContent accountId={accountId} profile={state.profile} steam={state.steam} stats={state.stats} />
    </PageContainer>
  );
}

function ProfileContent({
  accountId,
  profile,
  steam,
  stats
}: {
  accountId: string | null;
  profile: MyProfile;
  steam: SteamLink | null;
  stats: ProfileStats | null;
}) {
  const t = useTranslations("Profile");
  const prefix = useId();
  const [active, setActive] = useState<ProfileTabId>("overview");
  const library = useRemote("library", getLibraryStats);
  const friends = useRemote("friends", getFriends);
  const hardware = useRemote("hardware", getHardwareSummary);
  const onboarding = useRemote("onboarding", getOnboarding);

  return (
    <div className="flex flex-col gap-6">
      <ProfileHeader
        profile={profile}
        action={
          <Link href="/profile/edit" className={primaryLinkClass}>
            {t("editProfile")}
          </Link>
        }
      />
      <OnboardingPrompt onboarding={onboarding} />
      <ProfileStatsStrip friends={friends} library={library} hardware={hardware} stats={stats} />
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
            {tab === active ? (
              <TabBody
                tab={tab}
                accountId={accountId}
                profile={profile}
                steam={steam}
                library={library}
                friends={friends}
                hardware={hardware}
              />
            ) : null}
          </div>
        ))}
      </div>
    </div>
  );
}

function TabBody({
  tab,
  accountId,
  profile,
  steam,
  library,
  friends,
  hardware
}: {
  tab: ProfileTabId;
  accountId: string | null;
  profile: MyProfile;
  steam: SteamLink | null;
  library: Remote<LibraryStats>;
  friends: Remote<FriendsPage>;
  hardware: Remote<HardwareSummary>;
}) {
  switch (tab) {
    case "overview":
      return <ProfileOverview profile={profile} steam={steam} />;
    case "activity":
      return accountId ? <ProfileActivity userId={accountId} /> : null;
    case "games":
      return <ProfileGames library={library} />;
    case "friends":
      return <ProfileFriends friends={friends} />;
    case "hardware":
      return <ProfileHardware hardware={hardware} />;
  }
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
