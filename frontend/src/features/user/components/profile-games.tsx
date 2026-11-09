"use client";

import { useFormatter, useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import type { Remote } from "../hooks/use-remote";
import type { LibraryStats } from "../types/activity.types";
import { ProfileSection, RemoteBody, sectionLinkClass, StatBox } from "./remote-section";

/** Oyunlar sekmesi: kendi koleksiyonunun sayıları (`GET /library/entries/statistics`). Tamamlama oranı bu sayılardan hesaplanır. */
export function ProfileGames({ library }: { library: Remote<LibraryStats> }) {
  const t = useTranslations("Profile.games");
  const format = useFormatter();

  return (
    <ProfileSection title={t("title")}>
      <RemoteBody remote={library}>
        {(stats) =>
          stats.total === 0 ? (
            <div className="flex flex-wrap items-center gap-3">
              <p className="text-sm text-text-secondary">{t("empty")}</p>
              <Link href="/games" className={sectionLinkClass}>
                {t("browse")}
              </Link>
            </div>
          ) : (
            <>
              <dl className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                <StatBox label={t("total")} value={format.number(stats.total)} />
                <StatBox label={t("favorite")} value={format.number(stats.favorite)} />
                <StatBox label={t("completed")} value={format.number(stats.completed)} />
              </dl>
              <p className="mt-4 text-sm text-text-secondary">
                {t("completion", {
                  percent: format.number(Math.min(stats.completed / stats.total, 1), {
                    style: "percent",
                    maximumFractionDigits: 0
                  })
                })}
              </p>
            </>
          )
        }
      </RemoteBody>
    </ProfileSection>
  );
}
