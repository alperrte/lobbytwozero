"use client";

import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { Avatar } from "@/shared/components/ui/avatar";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { iconButtonClass } from "@/shared/components/ui/styles";
import { cn } from "@/shared/utils/cn";
import { useMessaging } from "../hooks/use-messaging";
import { usePeople } from "../hooks/use-people";
import { messagingErrorKey } from "../services/messaging-errors";
import type { Conversation, ConversationId, DeleteScope, Message, ThreadMessage } from "../types/messaging.types";
import { Composer } from "./composer";
import { GroupInfoDialog } from "./conversation-dialogs";
import { MessageBubble, type BubbleActions } from "./message-bubble";
import { DeleteMessageDialog, ForwardDialog, MessageInfoDialog, ReactionPickerDialog } from "./message-dialogs";

type Dialog =
  | { kind: "delete"; message: Message; scope: DeleteScope }
  | { kind: "forward"; message: Message }
  | { kind: "info"; message: Message }
  | { kind: "react"; message: Message }
  | { kind: "group" };

/** Alta bu kadar yakınsa yeni mesajda otomatik kaydırılır. */
const NEAR_BOTTOM_PX = 120;
const HIGHLIGHT_MS = 1600;

type ThreadViewProps = {
  conversationId: ConversationId;
  onBack: () => void;
  onLeft: () => void;
};

/**
 * Açık sohbet (MSG-07, MSG-09..12, MSG-19..26). Mesajlar `seq` sırasıyla çizilir; yukarı kaydırınca eski mesajlar yüklenir ve
 * kaydırma konumu korunur. Alttayken gelen mesaj görünür olur; yukarıdayken "Yeni mesajlar" düğmesi çıkar.
 */
export function ThreadView({ conversationId, onBack, onLeft }: ThreadViewProps) {
  const t = useTranslations("Messaging");
  const format = useFormatter();
  const { state, api, openThread, reloadThread, loadOlder, retry, discard, react } = useMessaging();
  const thread = state.threads[conversationId];
  const summary = state.conversations.find((item) => item.id === conversationId);
  const [detail, setDetail] = useState<{ id: ConversationId; conversation?: Conversation; error?: unknown } | null>(null);
  const [replyTo, setReplyTo] = useState<Message | null>(null);
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [highlight, setHighlight] = useState<string | null>(null);
  const [unseen, setUnseen] = useState(0);
  // "Altta mı" bilgisi hem ref'te (kaydırma etkisi için) hem state'te (çizimde yeni mesaj sayacı için) tutulur.
  const [atBottom, setAtBottom] = useState(true);
  const scrollRef = useRef<HTMLDivElement>(null);
  const stickToBottom = useRef(true);
  const restoreFrom = useRef<number | null>(null);

  // Sohbet değişince yerel görünüm durumu sıfırlanır.
  const [shownFor, setShownFor] = useState(conversationId);
  if (shownFor !== conversationId) {
    setShownFor(conversationId);
    setReplyTo(null);
    setDialog(null);
    setNotice(null);
    setUnseen(0);
    setAtBottom(true);
  }

  useEffect(() => {
    stickToBottom.current = true;
    openThread(conversationId);
    // `openThread` her durum değişiminde yenidir; yalnız sohbet değişince çağrılır.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversationId]);

  useEffect(() => {
    const controller = new AbortController();
    api.getConversation(conversationId, controller.signal).then(
      (conversation) => setDetail({ id: conversationId, conversation }),
      (error: unknown) => {
        if (!controller.signal.aborted) setDetail({ id: conversationId, error });
      }
    );
    return () => controller.abort();
  }, [api, conversationId, summary?.memberCount, summary?.title]);

  const conversation = detail?.id === conversationId ? detail.conversation : undefined;
  const type = conversation?.type ?? summary?.type ?? "DIRECT";
  const peerId =
    summary?.peerUserId ?? conversation?.members.find((member) => member.userId !== state.meId)?.userId ?? null;
  const messages = useMemo(() => thread?.messages ?? [], [thread?.messages]);

  const senderIds = useMemo(() => {
    const ids = new Set<string>();
    if (peerId) ids.add(peerId);
    for (const message of messages) {
      ids.add(message.senderId);
      if (message.replyTo?.senderId) ids.add(message.replyTo.senderId);
    }
    return [...ids];
  }, [messages, peerId]);
  const person = usePeople(senderIds);
  const peer = peerId ? person(peerId) : null;
  const title = type === "GROUP" ? conversation?.title ?? summary?.title ?? t("list.untitled") : peer?.name ?? t("list.untitled");
  const memberCount = conversation?.members.length ?? summary?.memberCount ?? 0;

  // Kaydırma: ilk yüklemede ve alttayken en alta; eski mesaj yüklenince konum korunur.
  const lastId = messages.at(-1)?.id;
  const lastMine = messages.at(-1)?.senderId === state.meId;
  useLayoutEffect(() => {
    const element = scrollRef.current;
    if (!element) return;
    if (restoreFrom.current !== null) {
      element.scrollTop = element.scrollHeight - restoreFrom.current;
      restoreFrom.current = null;
      return;
    }
    if (stickToBottom.current || lastMine) {
      element.scrollTop = element.scrollHeight;
    }
  }, [messages.length, lastId, lastMine]);

  const [seenLastId, setSeenLastId] = useState(lastId);
  if (seenLastId !== lastId) {
    setSeenLastId(lastId);
    if (!atBottom && !lastMine && lastId) setUnseen((count) => count + 1);
  }

  function onScroll() {
    const element = scrollRef.current;
    if (!element) return;
    const distance = element.scrollHeight - element.scrollTop - element.clientHeight;
    const near = distance < NEAR_BOTTOM_PX;
    stickToBottom.current = near;
    setAtBottom(near);
    if (near) setUnseen(0);
    if (element.scrollTop < 80 && thread?.hasMoreBefore && !thread.loadingOlder) {
      restoreFrom.current = element.scrollHeight - element.scrollTop;
      loadOlder(conversationId);
    }
  }

  const scrollToBottom = useCallback(() => {
    const element = scrollRef.current;
    if (!element) return;
    stickToBottom.current = true;
    element.scrollTo({ top: element.scrollHeight, behavior: "smooth" });
    setAtBottom(true);
    setUnseen(0);
  }, []);

  useEffect(() => {
    if (!highlight) return;
    const timer = setTimeout(() => setHighlight(null), HIGHLIGHT_MS);
    return () => clearTimeout(timer);
  }, [highlight]);

  const actions = useMemo<BubbleActions>(
    () => ({
      onReply: (message) => setReplyTo(message),
      onForward: (message) => setDialog({ kind: "forward", message }),
      onInfo: (message) => setDialog({ kind: "info", message }),
      onDelete: (message, scope) => setDialog({ kind: "delete", message, scope }),
      onMoreReactions: (message) => setDialog({ kind: "react", message }),
      onReact: (message, emoji) => {
        react(conversationId, message.id, emoji).catch((error: unknown) => setNotice(t(`errors.${messagingErrorKey(error)}`)));
      },
      onRetry: (message: ThreadMessage) => retry(conversationId, message.clientMessageId),
      onDiscard: (message: ThreadMessage) => discard(conversationId, message.clientMessageId),
      onJump: (messageId) => {
        const target = document.getElementById(`message-${messageId}`);
        if (target) {
          target.scrollIntoView({ behavior: "smooth", block: "center" });
          setHighlight(messageId);
        } else {
          setNotice(t("thread.replyNotLoaded"));
        }
      }
    }),
    [conversationId, discard, react, retry, t]
  );

  const notFound = thread?.status === "error" && messagingErrorKey(thread.error) === "conversationNotFound";

  return (
    <section aria-labelledby="messaging-thread-title" className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-panel border border-border bg-surface">
      <header className="flex items-center gap-3 border-b border-border px-3 py-2.5 sm:px-4">
        <button type="button" onClick={onBack} aria-label={t("thread.back")} className={cn(iconButtonClass, "lg:hidden")}>
          <Icon name="arrowLeft" />
        </button>
        {type === "GROUP" ? (
          <span className="inline-flex size-10 shrink-0 items-center justify-center rounded-full bg-primary/12 text-brand-text ring-1 ring-primary/40">
            <Icon name="users" className="size-5" />
          </span>
        ) : (
          <Avatar seed={peerId ?? conversationId} imageUrl={peer?.avatarUrl} size="sm" />
        )}
        <div className="min-w-0 flex-1">
          <h2 id="messaging-thread-title" className="truncate font-display text-lg font-semibold text-text-primary">
            {title}
          </h2>
          <p className="truncate text-xs text-text-secondary">
            {type === "GROUP" ? t("list.members", { count: memberCount }) : peer?.handle ?? t("thread.direct")}
          </p>
        </div>
        {type === "GROUP" ? (
          <button type="button" onClick={() => setDialog({ kind: "group" })} aria-label={t("group.info")} className={iconButtonClass}>
            <Icon name="info" />
          </button>
        ) : null}
      </header>

      <div
        ref={scrollRef}
        data-thread-scroll
        onScroll={onScroll}
        className="relative min-h-0 flex-1 overflow-y-auto overscroll-contain bg-background/40 py-3 [scrollbar-width:thin]"
      >
        {!thread || thread.status === "loading" ? (
          <p role="status" className="py-16 text-center text-sm text-text-secondary">{t("states.loading")}</p>
        ) : thread.status === "error" ? (
          <div role="alert" className="px-6 py-16 text-center">
            <p className="text-sm text-text-secondary">{t(`errors.${messagingErrorKey(thread.error)}`)}</p>
            {notFound ? (
              <button type="button" onClick={onLeft} className={buttonClass("secondary", "sm", "mt-4")}>
                {t("thread.backToList")}
              </button>
            ) : (
              <button type="button" onClick={() => reloadThread(conversationId)} className={buttonClass("secondary", "sm", "mt-4")}>
                <Icon name="refresh" className="size-4" />
                {t("states.retry")}
              </button>
            )}
          </div>
        ) : (
          <>
            {thread.hasMoreBefore ? (
              <div className="flex justify-center pb-2">
                <button
                  type="button"
                  disabled={thread.loadingOlder}
                  onClick={() => {
                    const element = scrollRef.current;
                    if (element) restoreFrom.current = element.scrollHeight - element.scrollTop;
                    loadOlder(conversationId);
                  }}
                  className={buttonClass("ghost", "sm")}
                >
                  {thread.loadingOlder ? t("states.loading") : t("thread.older")}
                </button>
              </div>
            ) : messages.length > 0 ? (
              <p className="pb-3 text-center text-xs text-text-secondary">{t("thread.start")}</p>
            ) : null}
            {thread.olderError ? (
              <p role="alert" className="pb-2 text-center text-xs text-brand-text">{t(`errors.${messagingErrorKey(thread.olderError)}`)}</p>
            ) : null}
            {messages.length === 0 ? (
              <div className="px-6 py-16 text-center">
                <span className="mx-auto inline-flex size-12 items-center justify-center rounded-full bg-surface-elevated text-text-secondary">
                  <Icon name="message" className="size-6" />
                </span>
                <p className="mt-3 font-display font-semibold">{t("thread.emptyTitle")}</p>
                <p className="mt-1 text-sm text-text-secondary">{t("thread.emptyBody")}</p>
              </div>
            ) : (
              <ol aria-label={t("thread.messages")} className="space-y-1">
                {messages.map((message, index) => {
                  const previous = messages[index - 1];
                  const day = new Date(message.createdAt).toDateString();
                  const newDay = !previous || new Date(previous.createdAt).toDateString() !== day;
                  const mine = message.senderId === state.meId;
                  const showSender = type === "GROUP" && !mine && (newDay || previous?.senderId !== message.senderId);
                  return (
                    <li key={message.local ? `local-${message.clientMessageId}` : message.id}>
                      {newDay ? (
                        <p className="my-3 flex justify-center">
                          <span className="rounded-full bg-surface-elevated px-3 py-1 text-xs font-medium text-text-secondary">
                            {format.dateTime(new Date(message.createdAt), { day: "numeric", month: "long", year: "numeric" })}
                          </span>
                        </p>
                      ) : null}
                      <MessageBubble
                        message={message}
                        mine={mine}
                        sender={person(message.senderId)}
                        replySender={message.replyTo?.senderId ? person(message.replyTo.senderId) : null}
                        showSender={showSender}
                        highlighted={highlight === message.id}
                        actions={actions}
                      />
                    </li>
                  );
                })}
              </ol>
            )}
          </>
        )}
      </div>

      {unseen > 0 ? (
        <div className="pointer-events-none relative">
          <button
            type="button"
            onClick={scrollToBottom}
            className={buttonClass("primary", "sm", "pointer-events-auto absolute bottom-3 start-1/2 -translate-x-1/2 shadow-float rtl:translate-x-1/2")}
          >
            <Icon name="chevron" className="size-4" />
            {t("thread.newMessages", { count: unseen })}
          </button>
        </div>
      ) : null}

      <p aria-live="polite" className={cn("px-4 text-xs text-text-secondary", notice ? "py-1.5" : "sr-only")}>
        {notice}
      </p>

      {notFound ? null : (
        <Composer
          conversationId={conversationId}
          replyTo={replyTo}
          replyAuthor={replyTo ? (replyTo.senderId === state.meId ? { ...person(replyTo.senderId), name: t("list.you") } : person(replyTo.senderId)) : null}
          onCancelReply={() => setReplyTo(null)}
          onSent={scrollToBottom}
        />
      )}

      {dialog?.kind === "delete" ? (
        <DeleteMessageDialog conversationId={conversationId} message={dialog.message} scope={dialog.scope} onClose={() => setDialog(null)} />
      ) : null}
      {dialog?.kind === "forward" ? (
        <ForwardDialog
          conversationId={conversationId}
          message={dialog.message}
          onClose={() => setDialog(null)}
          onDone={(count) => {
            setDialog(null);
            setNotice(t("forward.done", { count }));
          }}
        />
      ) : null}
      {dialog?.kind === "info" ? (
        <MessageInfoDialog conversationId={conversationId} message={dialog.message} mine={dialog.message.senderId === state.meId} onClose={() => setDialog(null)} />
      ) : null}
      {dialog?.kind === "react" ? (
        <ReactionPickerDialog
          onClose={() => setDialog(null)}
          onPick={(emoji) => {
            const message = dialog.message;
            setDialog(null);
            actions.onReact(message, emoji);
          }}
        />
      ) : null}
      {dialog?.kind === "group" ? (
        <GroupInfoDialog conversationId={conversationId} onClose={() => setDialog(null)} onLeft={onLeft} />
      ) : null}
    </section>
  );
}
