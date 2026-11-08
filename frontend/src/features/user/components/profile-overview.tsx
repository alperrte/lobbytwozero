"use client";

import type { ReactNode } from "react";
import { useLocale, useTranslations } from "next-intl";
import { cn } from "@/shared/utils/cn";
import { countryName } from "../catalog/countries";
import { useCatalogLabels } from "../hooks/use-catalog-labels";
import { previewProfile } from "../preview/preview-data";
import type { MyProfile, SteamLink } from "../types/profile.types";
import { CountryFlag } from "./country-flag";
import { PreviewMark } from "./preview-mark";
import { ProfileCompletion } from "./profile-completion";

const chipClass =
  "inline-flex items-center rounded-full border border-border bg-surface-elevated px-3 py-1 text-xs font-medium text-text-primary";

/** "Genel" sekmesi: biyografi, imza (önizleme), oyuncu tercihleri, bağlı hesaplar ve rozetler (önizleme). */
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
          <div className="mt-5 border-t border-border pt-4">
            <div className="flex flex-wrap items-center gap-3">
              <h3 className="text-sm font-medium text-text-secondary">{t("preview.signatureLabel")}</h3>
              <PreviewMark />
            </div>
            <p className="mt-2 max-w-prose italic text-text-secondary">{t("preview.signature")}</p>
          </div>
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

        <Card title={t("preview.badgesTitle")} mark>
          <ul className="grid grid-cols-2 gap-3">
            {previewProfile.badges.map((badge) => (
              <li
                key={badge}
                className="flex items-center gap-2 rounded-control border border-border bg-surface-elevated px-3 py-2"
              >
                <span aria-hidden="true" className="size-6 shrink-0 rounded-full bg-border" />
                <span className="min-w-0 truncate text-xs font-medium">{t(`preview.badge.${badge}`)}</span>
              </li>
            ))}
          </ul>
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

function Card({ title, mark = false, children }: { title: string; mark?: boolean; children: ReactNode }) {
  return (
    <section className="rounded-panel border border-border bg-surface p-5 sm:p-6">
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <h2 className="font-display text-base font-semibold">{title}</h2>
        {mark ? <PreviewMark /> : null}
      </div>
      {children}
    </section>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <dt className="text-sm text-text-secondary">{label}</dt>
      <dd className="text-sm text-text-primary">{children}</dd>
    </div>
  );
}

function Chips({ items }: { items: string[] }) {
  return items.length === 0 ? (
    <Empty />
  ) : (
    <ul className="flex flex-wrap gap-2">
      {items.map((item) => (
        <li key={item} className={chipClass}>
          {item}
        </li>
      ))}
    </ul>
  );
}

function Empty() {
  const t = useTranslations("Profile");
  return <span className="text-text-secondary">{t("none")}</span>;
}
