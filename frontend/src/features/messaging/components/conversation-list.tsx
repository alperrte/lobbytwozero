"use client";

import { useMemo, useState } from "react";
import { useFormatter, useNow, useTranslations } from "next-intl";
import { Avatar } from "@/shared/components/ui/avatar";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { SearchField } from "@/shared/components/ui/search-field";
import { cn } from "@/shared/utils/cn";
import { useMessaging } from "../hooks/use-messaging";
import { usePeople, type PersonView } from "../hooks/use-people";
import { messagingErrorKey } from "../services/messaging-errors";
import type { ConversationId, ConversationSummary } from "../types/messaging.types";
import { ConnectionStatus, DeliveryStatus, UnreadBadge, useMessageSnippet } from "./message-parts";

type ConversationListProps = {
  activeId: ConversationId | null;
  onSelect: (conversationId: ConversationId) => void;
  onNew: () => void;
};

/** Sohbet başlığı: grupta başlık, birebirde karşı tarafın adı. */
export function conversationTitle(summary: Pick<ConversationSummary, "type" | "title" | "peerUserId">, person: (id: string) => PersonView, fallback: string) {
  if (summary.type === "GROUP") return summary.title ?? fallback;
  return summary.peerUserId ? person(summary.peerUserId).name : fallback;
}

/**
 * Sohbet listesi (MSG-08, MSG-13): son etkinliğe göre sıralı, okunmamış rozetli, son mesaj önizlemeli. Arama yüklenmiş
 * sohbetlerde ad/başlığa göre süzer (sunucuda sohbet araması yoktur). Daha fazlası imleçli "daha fazla yükle" ile gelir.
 */
export function ConversationList({ activeId, onSelect, onNew }: ConversationListProps) {
  const t = useTranslations("Messaging");
  const { state, streamStatus, reloadList, loadMoreConversations } = useMessaging();
  const [query, setQuery] = useState("");
  const peerIds = useMemo(
    () => [
      ...new Set(
        state.conversations.flatMap((item) => [
          ...(item.peerUserId ? [item.peerUserId] : []),
          ...(item.type === "GROUP" && item.lastMessage ? [item.lastMessage.senderId] : [])
        ])
      )
    ],
    [state.conversations]
  );
  const person = usePeople(peerIds);

  const filtered = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase();
    if (!needle) return state.conversations;
    return state.conversations.filter((item) =>
      conversationTitle(item, person, t("list.untitled")).toLocaleLowerCase().includes(needle)
    );
  }, [state.conversations, query, person, t]);

  return (
    <section aria-labelledby="messaging-list-title" className="flex min-h-0 flex-1 flex-col rounded-panel border border-border bg-surface">
      <div className="space-y-3 border-b border-border p-4">
        <div className="flex items-center justify-between gap-3">
          <div className="min-w-0">
            <h1 id="messaging-list-title" className="font-display text-2xl font-bold tracking-tight text-text-primary">
              {t("list.title")}
            </h1>
            <ConnectionStatus status={streamStatus} />
          </div>
          <button type="button" onClick={onNew} className={buttonClass("primary", "sm")}>
            <Icon name="message" className="size-4" />
            {t("list.new")}
          </button>
        </div>
        <SearchField
          label={t("list.search")}
          placeholder={t("list.searchPlaceholder")}
          maxLength={100}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain p-2 [scrollbar-width:thin]">
        {state.listStatus === "loading" ? (
          <ListSkeleton label={t("states.loading")} />
        ) : state.listStatus === "error" ? (
          <div role="alert" className="px-4 py-10 text-center">
            <p className="text-sm text-text-secondary">{t(`errors.${messagingErrorKey(state.listError)}`)}</p>
            <button type="button" onClick={reloadList} className={buttonClass("secondary", "sm", "mt-4")}>
              <Icon name="refresh" className="size-4" />
              {t("states.retry")}
            </button>
          </div>
        ) : filtered.length === 0 ? (
          <div className="px-4 py-12 text-center">
            <span className="mx-auto inline-flex size-12 items-center justify-center rounded-full bg-surface-elevated text-text-secondary">
              <Icon name="messages" className="size-6" />
            </span>
            <p className="mt-3 font-display font-semibold text-text-primary">
              {query ? t("list.noMatchTitle") : t("list.emptyTitle")}
            </p>
            <p className="mt-1 text-sm text-text-secondary">{query ? t("list.noMatchBody") : t("list.emptyBody")}</p>
          </div>
        ) : (
          <ul className="space-y-0.5">
            {filtered.map((summary) => (
              <li key={summary.id}>
                <ConversationRow summary={summary} active={summary.id === activeId} person={person} onSelect={onSelect} />
              </li>
            ))}
          </ul>
        )}
        {state.listStatus === "ready" && state.nextCursor && !query ? (
          <div className="flex justify-center py-3">
            <button type="button" onClick={loadMoreConversations} className={buttonClass("ghost", "sm")}>
              {t("list.loadMore")}
            </button>
          </div>
        ) : null}
        {state.listError && state.listStatus === "ready" ? (
          <p role="alert" className="px-3 py-2 text-center text-xs text-brand-text">
            {t(`errors.${messagingErrorKey(state.listError)}`)}
          </p>
        ) : null}
      </div>
    </section>
  );
}

function ListSkeleton({ label }: { label: string }) {
  return (
    <div role="status" aria-live="polite">
      <span className="sr-only">{label}</span>
      <div aria-hidden="true" className="space-y-1">
        {Array.from({ length: 6 }, (_, index) => (
          <div key={index} className="flex items-center gap-3 rounded-panel p-3">
            <span className="size-10 shrink-0 animate-pulse rounded-full bg-surface-elevated motion-reduce:animate-none" />
            <span className="flex-1 space-y-2">
              <span className="block h-3.5 w-2/5 animate-pulse rounded-control bg-surface-elevated motion-reduce:animate-none" />
              <span className="block h-3 w-3/4 animate-pulse rounded-control bg-surface-elevated motion-reduce:animate-none" />
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function ConversationRow({
  summary,
  active,
  person,
  onSelect
}: {
  summary: ConversationSummary;
  active: boolean;
  person: (id: string) => PersonView;
  onSelect: (conversationId: ConversationId) => void;
}) {
  const t = useTranslations("Messaging");
  const format = useFormatter();
  const now = useNow({ updateInterval: 60_000 });
  const snippet = useMessageSnippet();
  const { state } = useMessaging();
  const title = conversationTitle(summary, person, t("list.untitled"));
  const peer = summary.peerUserId ? person(summary.peerUserId) : null;
  const last = summary.lastMessage;
  const mine = last?.senderId === state.meId;
  const prefix = last && !last.deleted ? (mine ? t("list.you") : summary.type === "GROUP" ? person(last.senderId).name : null) : null;
  const when = new Date(summary.lastActivityAt);
  const sameDay = when.toDateString() === now.toDateString();

  return (
    <button
      type="button"
      onClick={() => onSelect(summary.id)}
      aria-current={active ? "true" : undefined}
      className={cn(
        "flex w-full min-w-0 items-center gap-3 rounded-panel p-3 text-start transition-colors duration-150 hover:bg-surface-elevated focus-visible:outline-2 focus-visible:outline-focus motion-reduce:transition-none",
        active && "bg-surface-elevated ring-1 ring-inset ring-primary/40"
      )}
    >
      {summary.type === "GROUP" ? (
        <span className="inline-flex size-10 shrink-0 items-center justify-center rounded-full bg-primary/12 text-brand-text ring-1 ring-primary/40">
          <Icon name="users" className="size-5" />
        </span>
      ) : (
        <Avatar seed={summary.peerUserId ?? summary.id} imageUrl={peer?.avatarUrl} size="sm" />
      )}
      <span className="min-w-0 flex-1">
        <span className="flex items-baseline justify-between gap-2">
          <span className={cn("truncate font-semibold text-text-primary", summary.unreadCount > 0 && "font-bold")}>{title}</span>
          <time dateTime={summary.lastActivityAt} className="shrink-0 text-xs text-text-secondary">
            {sameDay ? format.dateTime(when, { hour: "2-digit", minute: "2-digit" }) : format.dateTime(when, { day: "numeric", month: "short" })}
          </time>
        </span>
        <span className="mt-0.5 flex items-center justify-between gap-2">
          <span className={cn("flex min-w-0 items-center gap-1 text-sm", summary.unreadCount > 0 ? "text-text-primary" : "text-text-secondary")}>
            {last && mine ? <DeliveryStatus message={last} className="shrink-0" /> : null}
            <span className="truncate">
              {last ? (
                <>
                  {prefix ? `${prefix}: ` : null}
                  <span className={cn(last.deleted && "italic")}>{snippet(last)}</span>
                </>
              ) : summary.type === "GROUP" ? (
                t("list.members", { count: summary.memberCount })
              ) : (
                t("list.noMessages")
              )}
            </span>
          </span>
          <UnreadBadge count={summary.unreadCount} label={t("list.unread", { count: summary.unreadCount >= 100 ? "99+" : summary.unreadCount })} />
        </span>
      </span>
    </button>
  );
}
