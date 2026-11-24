"use client";

import { useId } from "react";
import { useTranslations } from "next-intl";
import { Avatar } from "@/shared/components/ui/avatar";
import { apiUrl } from "@/shared/lib/api/client";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { useLabels } from "../../hooks/use-labels";
import { profileName, type PlayerSuggestionView } from "../../services/adapters";
import { ChipList, panelClass } from "../recommendation-ui";
import { FriendRequestButton } from "./friend-request-button";

/**
 * Oyuncu önerisi kartı. Gerekçe yalnız ortak tür/platform/oyuncu tipidir (skor bir benzerlik yüzdesi değildir, gösterilmez).
 * Profil kartı alınamazsa öneri düşürülmez; nötr "profil bilgisi alınamadı" metniyle gösterilir.
 */
export function PlayerSuggestionCard({ view, compact = false }: { view: PlayerSuggestionView; compact?: boolean }) {
  const t = useTranslations("Recommendation.social");
  const labels = useLabels();
  const titleId = useId();
  const { suggestion, profile } = view;
  const name = profileName(profile);
  const displayName = name ?? t("unknownPlayer");
  const avatarUrl = profile?.avatarPath ? apiUrl(profile.avatarPath as `/${string}`) : null;
  const username = profile?.username && profile.username !== name ? profile.username : null;

  return <article aria-labelledby={titleId} className={cn(panelClass, "flex min-w-0 flex-col gap-4", compact ? "p-3" : "p-4")}>
    <div className="flex min-w-0 items-center gap-3">
      <Avatar seed={suggestion.userId} imageUrl={avatarUrl} size={compact ? "sm" : "md"} ring={!compact} />
      <div className="min-w-0">
        <h3 id={titleId} className="truncate font-display font-semibold">
          {name
            ? <Link href={{ pathname: "/players/[userId]", params: { userId: suggestion.userId } }} className="rounded-sm hover:text-brand-text focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus">{displayName}</Link>
            : displayName}
        </h3>
        {username ? <p className="truncate text-xs text-text-secondary">@{username}</p> : null}
        {!profile ? <p className="text-xs text-text-secondary">{t("profileUnavailable")}</p> : null}
      </div>
    </div>
    {compact
      ? <ChipList label={t("sharedInterests")} items={[...suggestion.sharedGenres.map(labels.genre), ...suggestion.sharedPlatforms.map(labels.platform)].slice(0, 4)} />
      : <div className="space-y-3">
        <ChipList label={t("sharedGenres")} items={suggestion.sharedGenres.map(labels.genre)} />
        <ChipList label={t("sharedPlatforms")} items={suggestion.sharedPlatforms.map(labels.platform)} />
        <ChipList label={t("sharedPlayerTypes")} items={suggestion.sharedPlayerTypes.map(labels.playerType)} />
      </div>}
    <div className="mt-auto">
      <FriendRequestButton receiverId={suggestion.userId} name={displayName} compact={compact} />
    </div>
  </article>;
}
