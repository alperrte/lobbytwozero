"use client";

import { useMemo } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { useRemote, type Remote } from "../hooks/use-remote";
import { getProfileCards } from "../services/profile-api";
import type { FriendsPage, PlayerCard } from "../types/activity.types";
import { ProfileAvatar } from "./profile-avatar";
import { ProfileSection, RemoteBody, sectionLinkClass } from "./remote-section";

/**
 * Arkadaşlar sekmesi: `GET /social/friends` yalnız kimlik verir; ad ve avatar User'ın toplu kart ucundan gelir. Kartı
 * olmayan (devre dışı ya da engel ilişkili) hesaplar sunucuda atlanır ve listede görünmez. Toplam sayı sunucudan gelmez:
 * ilk sayfadan sonrası varsa "N+" yazılır.
 */
export function ProfileFriends({ friends }: { friends: Remote<FriendsPage> }) {
  const t = useTranslations("Profile.friendsTab");

  return (
    <ProfileSection
      title={t("title")}
      action={
        <Link href="/players" className={sectionLinkClass}>
          {t("find")}
        </Link>
      }
    >
      <RemoteBody remote={friends}>
        {(page) => (page.items.length === 0 ? <p className="text-sm text-text-secondary">{t("empty")}</p> : <FriendsGrid page={page} />)}
      </RemoteBody>
    </ProfileSection>
  );
}

function FriendsGrid({ page }: { page: FriendsPage }) {
  const t = useTranslations("Profile.friendsTab");
  const key = useMemo(() => page.items.map((item) => item.friendId).join(","), [page.items]);
  const load = useMemo(
    () => (signal: AbortSignal) => getProfileCards(key.split(","), signal),
    [key]
  );
  const cards = useRemote(key, load);
  const since = useMemo(() => new Map(page.items.map((item) => [item.friendId, item.since])), [page.items]);

  return (
    <RemoteBody remote={cards}>
      {(list) => (
        <>
          <p className="mb-4 text-sm text-text-secondary">
            {page.hasMore ? t("countMore", { count: page.items.length }) : t("count", { count: page.items.length })}
            {list.length < page.items.length ? ` · ${t("someHidden")}` : ""}
          </p>
          {list.length === 0 ? (
            <p className="text-sm text-text-secondary">{t("noneVisible")}</p>
          ) : (
            <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {list.map((card) => (
                <li key={card.userId}>
                  <FriendRow card={card} since={since.get(card.userId) ?? null} />
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </RemoteBody>
  );
}

function FriendRow({ card, since }: { card: PlayerCard; since: string | null }) {
  const t = useTranslations("Profile.friendsTab");
  const format = useFormatter();
  const name = card.displayName ?? card.username ?? t("unnamed");

  return (
    <Link
      href={{ pathname: "/players/[userId]", params: { userId: card.userId } }}
      aria-label={t("open", { name })}
      className="flex items-center gap-3 rounded-control border border-border bg-surface-elevated p-3 transition-colors duration-150 hover:border-control-border"
    >
      <ProfileAvatar name={name} src={card.avatar} className="size-11 shrink-0" initialClassName="text-base" />
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-semibold text-text-primary">{name}</span>
        {card.username && card.displayName ? (
          <span className="block truncate text-xs text-text-secondary">@{card.username}</span>
        ) : null}
        {since ? (
          <span className="block truncate text-xs text-text-secondary">
            {t("since", { date: format.dateTime(new Date(since), { dateStyle: "medium" }) })}
          </span>
        ) : null}
      </span>
    </Link>
  );
}
