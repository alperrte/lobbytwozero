"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import type { PagedList } from "../hooks/use-paged-social-list";
import { useFriends, useSocialCounts } from "../hooks/use-social-data";
import { SOCIAL_PREVIEW_DATA } from "../services/social-service";
import type { FriendView, PresenceStatus } from "../types/social.types";
import { ADD_BY_CODE_INPUT_ID } from "./add-by-code-form";
import { FriendCard, type FriendIntent } from "./friend-card";
import { EmptyState, ErrorState, ListSkeleton, LoadMore } from "./list-states";
import { PersonActionDialog, type PersonAction } from "./person-action-dialog";
import { SearchField } from "./search-field";
import { IncomingRequestsPanel, InviteAndAddPanel } from "./side-panels";
import { SocialPage } from "./social-page";

type Filter = "all" | Exclude<PresenceStatus, "offline">;

const filters: ReadonlyArray<{ id: Filter; label: "filterAll" | "filterOnline" | "filterInGame" }> = [
  { id: "all", label: "filterAll" },
  { id: "online", label: "filterOnline" },
  { id: "inGame", label: "filterInGame" }
];

function matches(view: FriendView, query: string, locale: string): boolean {
  if (!query) {
    return true;
  }
  const needle = query.toLocaleLowerCase(locale);
  return [view.profile.displayName, view.profile.username].some(
    (value) => value?.toLocaleLowerCase(locale).includes(needle) ?? false
  );
}

/** Hero'daki "Arkadaş Ekle": kodla ekleme alanına kaydırır ve odaklar (ayrı bir pencere açmaz). */
function focusAddByCode() {
  const input = document.getElementById(ADD_BY_CODE_INPUT_ID);
  if (input instanceof HTMLInputElement) {
    input.scrollIntoView({ block: "center", behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
    input.focus({ preventScroll: true });
  }
}

export function FriendsView() {
  const t = useTranslations("Social");
  return (
    <SocialPage
      section="friends"
      icon="users"
      title={t("hero.friendsTitle")}
      subtitle={t("hero.friendsSubtitle")}
      preview={SOCIAL_PREVIEW_DATA}
      heroAction={
        <button type="button" onClick={focusAddByCode} className={buttonClass("primary", "md", "h-11 px-5 text-base")}>
          <Icon name="userPlus" className="size-5" />
          {t("hero.addFriend")}
        </button>
      }
      asideLabel={t("hero.addFriend")}
      aside={
        <>
          <IncomingRequestsPanel />
          <InviteAndAddPanel />
        </>
      }
    >
      <FriendsList />
    </SocialPage>
  );
}

function FriendsList() {
  const t = useTranslations("Social");
  const locale = useLocale();
  const friends = useFriends();
  const counts = useSocialCounts();
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<Filter>("all");
  const [action, setAction] = useState<PersonAction | null>(null);

  const searched = friends.items.filter((view) => matches(view, query.trim(), locale));
  const shown = filter === "all" ? searched : searched.filter((view) => view.presence.status === filter);
  const tally = (status: Exclude<Filter, "all">) => searched.filter((view) => view.presence.status === status).length;
  // Arama ve süzgeç istemcide, yalnız yüklenmiş sayfalarda çalışır (serviste arama ucu yoktur); eksik kalan varsa söylenir.
  const narrowed = query.trim() !== "" || filter !== "all";
  const partial = narrowed && friends.hasMore && !friends.loading;
  const friendsCount = counts.data?.friends;
  const overflow = friendsCount?.more ? "+" : "";
  const total = friendsCount ? `${friendsCount.count}${overflow}` : String(friends.items.length);

  return (
    <section aria-label={t("friends.listLabel")} className="space-y-4">
      {/* Yerleşim ekran değil araç çubuğu genişliğine bağlıdır: yan panelli dar sütunda da arama ezilmez, süzgeç taşmaz. */}
      <div className="@container rounded-panel border border-border bg-surface p-3">
        <div className="flex flex-col gap-3 @min-[40rem]:flex-row @min-[40rem]:items-center">
          <SearchField
            label={t("friends.searchLabel")}
            placeholder={t("friends.searchPlaceholder")}
            value={query}
            onChange={setQuery}
          />
          <StatusFilter value={filter} onChange={setFilter} tally={friends.loading ? null : tally} />
        </div>
      </div>

      {partial ? (
        <output className="flex items-start gap-2 px-1 text-sm text-text-secondary">
          <Icon name="info" className="mt-0.5 size-4 shrink-0" />
          {t("friends.partialResults", { loaded: friends.items.length })}
        </output>
      ) : null}

      <FriendsResults
        friends={friends}
        shown={shown}
        locked={action !== null}
        onIntent={(intent, view) => setAction({ kind: intent, person: view })}
      />

      {!friends.loading && friends.items.length > 0 ? (
        <div className="flex flex-col items-center justify-between gap-3 sm:flex-row">
          <p className="text-sm text-text-secondary" aria-live="polite">
            {t("friends.showing", { shown: shown.length, total })}
          </p>
          {friends.hasMore ? (
            <LoadMore
              label={t("friends.loadMore")}
              loading={friends.loadingMore}
              failed={Boolean(friends.loadMoreError)}
              onLoad={friends.loadMore}
            />
          ) : null}
        </div>
      ) : null}

      <PersonActionDialog action={action} onClose={() => setAction(null)} />
    </section>
  );
}

/**
 * Tümü / Çevrimiçi / Oyunda süzgeci. `tally` null ise (liste yükleniyor) sayılar gösterilmez.
 * Dar alanda üç sütunlu ızgaradır (yatay kaydırma yok, düğme gizli kalmaz); çok dar alanda süs ikonları gizlenir, etiket
 * gerekirse kısaltılır. Geniş alanda arama kutusunun yanında tek satırdır.
 */
function StatusFilter({
  value,
  onChange,
  tally
}: Readonly<{
  value: Filter;
  onChange: (filter: Filter) => void;
  tally: ((status: Exclude<Filter, "all">) => number) | null;
}>) {
  const t = useTranslations("Social.friends");
  return (
    <fieldset
      aria-label={t("filterLabel")}
      className="grid min-w-0 grid-cols-[auto_1fr_1fr] gap-2 @min-[40rem]:flex @min-[40rem]:shrink-0"
    >
      {filters.map((option) => {
        const pressed = value === option.id;
        return (
          <button
            key={option.id}
            type="button"
            aria-pressed={pressed}
            onClick={() => onChange(option.id)}
            className={cn(
              "inline-flex h-10 min-w-0 items-center justify-center gap-1.5 rounded-control border px-2.5 text-sm font-semibold transition-colors duration-150 motion-reduce:transition-none @min-[26rem]:gap-2 @min-[26rem]:px-3.5 @min-[40rem]:shrink-0",
              pressed
                ? "border-primary bg-primary/12 text-text-primary"
                : "border-border bg-surface-elevated/60 text-text-secondary hover:border-control-border hover:text-text-primary"
            )}
          >
            {option.id === "online" ? (
              <span aria-hidden="true" className="size-2 shrink-0 rounded-full bg-success @max-[24rem]:hidden" />
            ) : null}
            {option.id === "inGame" ? (
              <Icon name="gamepad" className="size-4 shrink-0 text-warning @max-[24rem]:hidden" />
            ) : null}
            <span className="min-w-0 truncate">{t(option.label)}</span>
            {option.id !== "all" && tally ? (
              <span className="shrink-0 rounded-full bg-surface-elevated px-1.5 text-xs tabular-nums text-text-primary">
                {tally(option.id)}
              </span>
            ) : null}
          </button>
        );
      })}
    </fieldset>
  );
}

/** Liste gövdesi: yükleniyor → hata → hiç arkadaş yok → eşleşme yok → kartlar. */
function FriendsResults({
  friends,
  shown,
  locked,
  onIntent
}: Readonly<{
  friends: PagedList<FriendView>;
  shown: readonly FriendView[];
  locked: boolean;
  onIntent: (intent: FriendIntent, view: FriendView) => void;
}>) {
  const t = useTranslations("Social");
  if (friends.loading) {
    return <ListSkeleton variant="cards" count={4} />;
  }
  if (friends.error && friends.items.length === 0) {
    return <ErrorState error={friends.error} onRetry={friends.retry} />;
  }
  if (friends.items.length === 0) {
    return (
      <EmptyState
        icon="users"
        title={t("friends.emptyTitle")}
        body={t("friends.emptyBody")}
        action={
          <button type="button" onClick={focusAddByCode} className={buttonClass("primary")}>
            <Icon name="userPlus" className="size-4" />
            {t("hero.addFriend")}
          </button>
        }
      />
    );
  }
  if (shown.length === 0) {
    return <EmptyState icon="search" title={t("friends.noMatchTitle")} body={t("friends.noMatchBody")} />;
  }
  return (
    // Sütun sayısı ekran değil liste genişliğine bağlıdır: kart 18rem'den daralırsa oyun adı kesilir.
    <ul className="grid grid-cols-[repeat(auto-fill,minmax(min(100%,18rem),1fr))] gap-4">
      {shown.map((view) => (
        <li key={view.friend.friendId} className="flex">
          <FriendCard view={view} onIntent={onIntent} disabled={locked} />
        </li>
      ))}
    </ul>
  );
}
