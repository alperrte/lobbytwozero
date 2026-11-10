"use client";

import { useLocale, useTranslations } from "next-intl";
import { cn } from "@/shared/utils/cn";
import { countryName } from "../catalog/countries";
import { useCatalogLabels } from "../hooks/use-catalog-labels";
import type { MyProfile, SteamLink } from "../types/profile.types";
import { CountryFlag } from "./country-flag";
import { ProfileCompletion } from "./profile-completion";
import { Card, Chips, Empty, Row } from "./profile-parts";

/** "Genel" sekmesi: biyografi, oyuncu tercihleri, profil tamamlama ve bağlı hesaplar. */
export function ProfileOverview({ profile, steam }: { profile: MyProfile; steam: SteamLink | null }) {
  const t = useTranslations("Profile");
  const locale = useLocale();
  const labels = useCatalogLabels();
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
      <div className="flex flex-col gap-6">
        <Card title={t("about")}>
          {/* Biyografi düz metindir: HTML olarak yorumlanmaz, satır sonları korunur. */}
          {profile.bio ? (
            <p className="max-w-prose whitespace-pre-line text-text-primary">{profile.bio}</p>
          ) : (
            <p className="text-text-secondary">{t("emptyBio")}</p>
          )}
        </Card>

        <Card title={t("preferences")}>
          <dl className="flex flex-col gap-4">
            <Row label={t("country")}>
              {profile.country ? (
                <span className="inline-flex items-center gap-2">
                  <CountryFlag code={profile.country} className="h-4 w-6" />
                  <span>{countryName(profile.country, locale)}</span>
                  <span className="text-text-secondary">{profile.country}</span>
                </span>
              ) : (
                <Empty />
              )}
            </Row>
            <Row label={t("playerTypes")}>
              <Chips items={profile.playerTypes.map((type) => t(`playerType.${type}`))} />
            </Row>
            <Row label={t("genres")}>
              <Chips items={profile.favoriteGenres.map(labels.genre)} />
            </Row>
            <Row label={t("platforms")}>
              <Chips items={profile.favoritePlatforms.map(labels.platform)} />
            </Row>
          </dl>
        </Card>
      </div>

      <div className="flex flex-col gap-6">
        <ProfileCompletion profile={profile} steam={steam} />
        <Card title={t("linkedAccounts")}>
          <SteamRow steam={steam} />
        </Card>
      </div>
    </div>
  );
}

function SteamRow({ steam }: { steam: SteamLink | null }) {
  const t = useTranslations("Profile.steam");
  const tone = steam?.linked ? "bg-success" : "bg-text-secondary";
  return (
    <div className="flex items-center gap-3 rounded-control border border-border bg-surface-elevated px-3 py-3">
      <span aria-hidden="true" className={cn("size-2.5 shrink-0 rounded-full", tone)} />
      <div className="min-w-0 flex-1">
        <p className="font-display text-sm font-semibold">{t("name")}</p>
        <p className="text-xs text-text-secondary">
          {steam === null
            ? t("unknown")
            : steam.linked
              ? steam.showOnProfile
                ? t("linkedPublic")
                : t("linkedPrivate")
              : t("notLinked")}
        </p>
      </div>
    </div>
  );
}
