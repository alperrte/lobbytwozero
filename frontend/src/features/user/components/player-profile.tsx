"use client";

import { useCallback } from "react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import { useSession } from "@/features/auth";
import { PageContainer } from "@/shared/components/layout/page-container";
import { Link } from "@/shared/lib/i18n/navigation";
import { countryName } from "../catalog/countries";
import { useCatalogLabels } from "../hooks/use-catalog-labels";
import { useRemote } from "../hooks/use-remote";
import { getPublicProfile } from "../services/players-api";
import type { PublicProfile } from "../types/players.types";
import type { CountSection } from "../types/profile.types";
import { CountryFlag } from "./country-flag";
import { primaryLinkClass, ProfileHeader } from "./profile-header";
import { Card, Chips, Empty, Row } from "./profile-parts";
import { ProfileStateNotice } from "./profile-state-notice";
import { sectionLinkClass, StatBox } from "./remote-section";

/**
 * Başkasının açık profili (USER-05): sunucunun izleyiciye göre süzdüğü veriyi gösterir. Gizli bölüm "gizli", kaynakta henüz
 * veri yoksa "henüz yok" yazılır; sayı uydurulmaz. Var olmayan, gizli ve engelli profiller sunucuda aynı 404 olduğundan burada da
 * tek bir "bulunamadı" ekranı vardır. `userKey` kimlik (UUID) ya da kullanıcı adıdır.
 */
export function PlayerProfile({ userKey }: { userKey: string }) {
  const t = useTranslations("Players.profile");
  const load = useCallback((signal: AbortSignal) => getPublicProfile(userKey, signal), [userKey]);
  const { state, retry } = useRemote(userKey, load);

  if (state.status === "idle" || state.status === "loading") {
    return (
      <PageContainer>
        <div role="status" aria-live="polite" className="animate-pulse rounded-float border border-border bg-surface">
          <span className="sr-only">{t("loading")}</span>
          <div className="h-40 rounded-t-float bg-surface-elevated sm:h-56" />
          <div className="flex flex-col gap-3 px-5 py-6 sm:px-6">
            <div className="h-6 w-48 rounded-control bg-surface-elevated" />
            <div className="h-4 w-32 rounded-control bg-surface-elevated" />
          </div>
        </div>
      </PageContainer>
    );
  }
  if (state.status === "error") {
    if (state.code === "PROFILE_NOT_FOUND") {
      return (
        <PageContainer>
          <section role="alert" className="max-w-prose rounded-panel border border-border bg-surface p-5 sm:p-6">
            <h2 className="font-display text-lg font-semibold">{t("notFoundTitle")}</h2>
            <p className="mt-2 text-text-secondary">{t("notFoundBody")}</p>
            <Link href="/players" className={`mt-4 ${sectionLinkClass}`}>
              {t("search")}
            </Link>
          </section>
        </PageContainer>
      );
    }
    return (
      <PageContainer>
        <ProfileStateNotice title={t("errorTitle")} body={t("errorBody")} actionLabel={t("retry")} onAction={retry} />
      </PageContainer>
    );
  }
  return (
    <PageContainer>
      <PlayerContent profile={state.data} />
    </PageContainer>
  );
}

function PlayerContent({ profile }: { profile: PublicProfile }) {
  const t = useTranslations("Players.profile");
  const session = useSession();
  const own = session.status === "authenticated" && session.me.accountId.toLowerCase() === profile.userId.toLowerCase();

  return (
    <div className="flex flex-col gap-6">
      <ProfileHeader
        profile={profile}
        action={
          own ? (
            <Link href="/profile" className={primaryLinkClass}>
              {t("ownProfile")}
            </Link>
          ) : (
            <Link
              href={{ pathname: "/community/users/[userId]", params: { userId: profile.userId } }}
              className={sectionLinkClass}
            >
              {t("posts")}
            </Link>
          )
        }
      />
      <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div className="flex flex-col gap-6">
          {profile.details ? <DetailsCard details={profile.details} /> : <DetailsHidden guest={session.status === "guest"} />}
        </div>
        <div className="flex flex-col gap-6">
          <SummaryCard profile={profile} />
          {profile.details?.steam ? <SteamCard url={profile.details.steam.profileUrl} /> : null}
        </div>
      </div>
    </div>
  );
}

function DetailsCard({ details }: { details: NonNullable<PublicProfile["details"]> }) {
  const t = useTranslations("Profile");
  const locale = useLocale();
  const labels = useCatalogLabels();
  return (
    <>
      <Card title={t("about")}>
        {/* Biyografi düz metindir: HTML olarak yorumlanmaz, satır sonları korunur. */}
        {details.bio ? (
          <p className="max-w-prose whitespace-pre-line text-text-primary">{details.bio}</p>
        ) : (
          <p className="text-text-secondary">{t("emptyBio")}</p>
        )}
      </Card>
      <Card title={t("preferences")}>
        <dl className="flex flex-col gap-4">
          <Row label={t("country")}>
            {details.country ? (
              <span className="inline-flex items-center gap-2">
                <CountryFlag code={details.country} className="h-4 w-6" />
                <span>{countryName(details.country, locale)}</span>
              </span>
            ) : (
              <Empty />
            )}
          </Row>
          <Row label={t("playerTypes")}>
            <Chips items={details.playerTypes.map((type) => t(`playerType.${type}`))} />
          </Row>
          <Row label={t("genres")}>
            <Chips items={details.favoriteGenres.map(labels.genre)} />
          </Row>
          <Row label={t("platforms")}>
            <Chips items={details.favoritePlatforms.map(labels.platform)} />
          </Row>
        </dl>
      </Card>
    </>
  );
}

/** Ayrıntılar gelmediyse nedeni açıklanır: misafire giriş önerilir, üyeye oyuncunun paylaşmadığı söylenir. */
function DetailsHidden({ guest }: { guest: boolean }) {
  const t = useTranslations("Players.profile");
  return (
    <Card title={t("detailsTitle")}>
      <div className="flex flex-wrap items-center gap-3">
        <p className="text-sm text-text-secondary">{guest ? t("detailsGuest") : t("detailsHidden")}</p>
        {guest ? (
          <Link href="/login" className={sectionLinkClass}>
            {t("signIn")}
          </Link>
        ) : null}
      </div>
    </Card>
  );
}

function SummaryCard({ profile }: { profile: PublicProfile }) {
  const t = useTranslations("Players.profile.summary");
  const format = useFormatter();

  function value(section: CountSection): string {
    if (section.status === "AVAILABLE" && section.count !== null) return format.number(section.count);
    return section.status === "HIDDEN" ? t("hidden") : t("unavailable");
  }

  return (
    <Card title={t("title")}>
      <dl className="grid grid-cols-1 gap-3">
        <StatBox label={t("friends")} value={value(profile.summary.friends)} />
        <StatBox label={t("library")} value={value(profile.summary.library)} />
        <StatBox label={t("reviews")} value={value(profile.summary.reviews)} />
      </dl>
    </Card>
  );
}

function SteamCard({ url }: { url: string }) {
  const t = useTranslations("Players.profile.steam");
  return (
    <Card title={t("title")}>
      {/* Adres servis katmanında https://steamcommunity.com/profiles/<17 hane> biçimine doğrulanmıştır. */}
      <a href={url} target="_blank" rel="noopener noreferrer" className={sectionLinkClass}>
        {t("open")}
        <span className="sr-only"> {t("newTab")}</span>
      </a>
    </Card>
  );
}
