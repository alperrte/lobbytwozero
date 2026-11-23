"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useReducer,
  useRef,
  useState,
  type ReactNode
} from "react";
import { messagingApi, type MessagingSource } from "../services/messaging-api";
import { isAbort, messagingErrorKey } from "../services/messaging-errors";
import { openMessagingStream } from "../services/messaging-stream";
import { newClientMessageId } from "../schemas/message-body";
import type {
  Attachment,
  Conversation,
  ConversationId,
  DeleteScope,
  GifItem,
  Message,
  MessageId,
  SendMessageInput,
  StreamStatus,
  ThreadMessage,
  UserId
} from "../types/messaging.types";
import { initialMessagingState, latestSeq, messagingReducer, type MessagingState } from "./messaging-reducer";

/**
 * Mesajlaşma ekranının veri katmanı (component → hook → service). Tek bir canlı akış açar, sohbet listesini ve açık sohbetleri
 * reducer'da tutar, iyimser gönderimi ve okundu işaretlemeyi yönetir.
 *
 * - Gönderim: mesaj önce "gönderiliyor" olarak görünür; başarısızsa "tekrar dene" aynı `clientMessageId` ile yapılır
 *   (sunucu yeniden denemede aynı mesajı `200` ile döner, çift mesaj olmaz; web-rules §7.2).
 * - Yeniden bağlanınca (ADR 0001): liste yenilenir, açık sohbet son sayfadan yeniden okunur (kaçan silme/tepki olayları dahil).
 * - Okundu: açık sohbet sekmede görünürken en son `seq` sunucuya bildirilir (yalnız ileri gider).
 */

export type SendDraft = {
  body?: string;
  attachment?: Attachment;
  /** Görsel önizlemesi (yerel `blob:` adresi); gönderim sürerken gösterilir. */
  previewUrl?: string;
  gif?: GifItem;
  replyTo?: Message;
};

type MessagingContextValue = {
  state: MessagingState;
  streamStatus: StreamStatus;
  api: MessagingSource;
  reloadList: () => void;
  loadMoreConversations: () => void;
  openThread: (conversationId: ConversationId) => void;
  reloadThread: (conversationId: ConversationId) => void;
  loadOlder: (conversationId: ConversationId) => void;
  setActive: (conversationId: ConversationId | null) => void;
  send: (conversationId: ConversationId, draft: SendDraft) => void;
  retry: (conversationId: ConversationId, clientMessageId: string) => void;
  discard: (conversationId: ConversationId, clientMessageId: string) => void;
  react: (conversationId: ConversationId, messageId: MessageId, emoji: string | null) => Promise<void>;
  remove: (conversationId: ConversationId, messageId: MessageId, scope: DeleteScope) => Promise<void>;
  forward: (conversationId: ConversationId, messageId: MessageId, targets: ConversationId[]) => Promise<void>;
  openDirect: (userId: UserId) => Promise<Conversation>;
  createGroup: (title: string, memberIds: UserId[]) => Promise<Conversation>;
  conversationChanged: (conversation: Conversation) => void;
  conversationLeft: (conversationId: ConversationId) => void;
};

const MessagingContext = createContext<MessagingContextValue | null>(null);

const LIST_PAGE = 20;
const HISTORY_PAGE = 30;
const SYNC_PAGE = 100;

function emptyMessage(conversationId: ConversationId, meId: UserId, clientMessageId: string): Message {
  return {
    id: `local:${clientMessageId}`,
    conversationId,
    seq: Number.MAX_SAFE_INTEGER,
    senderId: meId,
    type: "TEXT",
    body: null,
    clientMessageId,
    createdAt: new Date().toISOString(),
    readStatus: null,
    attachment: null,
    gif: null,
    replyTo: null,
    forwarded: false,
    forwardedManyTimes: false,
    deleted: false,
    reactions: { summary: [], mine: null },
    deletableForEveryoneUntil: null
  };
}

/** Yerel (iyimser) mesaj: sunucu kaydı gelene kadar aynı görünümle çizilir. */
export function localMessage(conversationId: ConversationId, meId: UserId, draft: SendDraft): ThreadMessage {
  const clientMessageId = newClientMessageId();
  const input: SendMessageInput = { clientMessageId };
  const body = draft.body?.length ? draft.body : undefined;
  if (body) input.body = body;
  if (draft.attachment) input.attachmentId = draft.attachment.id;
  if (draft.gif) input.gif = { provider: "GIPHY", id: draft.gif.id };
  if (draft.replyTo) input.replyToMessageId = draft.replyTo.id;
  const base = emptyMessage(conversationId, meId, clientMessageId);
  return {
    ...base,
    type: draft.attachment ? (draft.attachment.mimeType.startsWith("audio/") ? "VOICE" : "IMAGE") : draft.gif ? "GIF" : "TEXT",
    body: body ?? null,
    attachment: draft.attachment ? { ...draft.attachment, url: draft.previewUrl ?? draft.attachment.url } : null,
    gif: draft.gif
      ? { provider: "GIPHY", id: draft.gif.id, url: draft.gif.url, previewUrl: draft.gif.previewUrl, width: draft.gif.width, height: draft.gif.height }
      : null,
    replyTo: draft.replyTo
      ? {
          messageId: draft.replyTo.id,
          seq: draft.replyTo.seq,
          senderId: draft.replyTo.senderId,
          type: draft.replyTo.type,
          snippet: draft.replyTo.body ? draft.replyTo.body.slice(0, 100) : null,
          deleted: false,
          unavailable: false
        }
      : null,
    local: { state: "sending", input }
  };
}

export function MessagingProvider({
  meId,
  children,
  api = messagingApi,
  stream = openMessagingStream
}: {
  meId: UserId;
  children: ReactNode;
  api?: MessagingSource;
  stream?: typeof openMessagingStream;
}) {
  const [state, dispatch] = useReducer(messagingReducer, meId, initialMessagingState);
  const [streamStatus, setStreamStatus] = useState<StreamStatus>("connecting");
  const stateRef = useRef(state);
  useEffect(() => {
    stateRef.current = state;
  });

  const listRequest = useRef<AbortController | null>(null);

  const loadList = useCallback(
    (append: boolean) => {
      const cursor = append ? stateRef.current.nextCursor : null;
      if (append && !cursor) return;
      listRequest.current?.abort();
      const controller = new AbortController();
      listRequest.current = controller;
      if (!append) dispatch({ type: "listLoading" });
      api.listConversations({ limit: LIST_PAGE, cursor }, controller.signal).then(
        (page) => dispatch({ type: "listLoaded", page, append }),
        (error: unknown) => {
          if (!isAbort(error) && !controller.signal.aborted) dispatch({ type: "listFailed", error });
        }
      );
    },
    [api]
  );

  const loadThread = useCallback(
    (conversationId: ConversationId) => {
      dispatch({ type: "threadLoading", conversationId });
      api.history(conversationId, { limit: HISTORY_PAGE }).then(
        (page) => dispatch({ type: "threadLoaded", conversationId, page }),
        (error: unknown) => {
          dispatch({ type: "threadFailed", conversationId, error });
          if (messagingErrorKey(error) === "conversationNotFound") {
            dispatch({ type: "conversationRemoved", conversationId });
          }
        }
      );
    },
    [api]
  );

  /** Kaçırılan yeni mesajları `after` ile kayıpsız çeker; ardından son sayfayı yeniler (silme/tepki olayları `id` taşımaz). */
  const resyncThread = useCallback(
    async (conversationId: ConversationId) => {
      let after = latestSeq(stateRef.current.threads[conversationId]);
      try {
        for (let round = 0; round < 10; round += 1) {
          const page = await api.history(conversationId, { after, limit: SYNC_PAGE });
          dispatch({ type: "threadSynced", conversationId, messages: page.items });
          if (!page.hasMore || page.items.length === 0) break;
          after = Math.max(after, ...page.items.map((item) => item.seq));
        }
      } catch {
        // Eşitleme en iyi çabadır; bir sonraki bağlantıda yeniden denenir.
      }
      loadThread(conversationId);
    },
    [api, loadThread]
  );

  // İlk liste ve tek canlı akış.
  useEffect(() => {
    loadList(false);
    const stop = stream({
      onEvent: (event) => dispatch({ type: "stream", event }),
      onStatus: setStreamStatus,
      onResync: () => {
        loadList(false);
        const active = stateRef.current.activeId;
        if (active) void resyncThread(active);
      }
    });
    return () => {
      stop();
      listRequest.current?.abort();
    };
  }, [loadList, resyncThread, stream]);

  // Bilinmeyen sohbetten mesaj geldi (ör. biri bizi gruba ekledi): listeyi sunucudan yenile.
  useEffect(() => {
    if (state.listStale) loadList(false);
  }, [state.listStale, loadList]);

  // Okundu işaretleme: açık sohbet görünürken en son sunucu `seq`'i bildirilir.
  const lastMarked = useRef(new Map<ConversationId, number>());
  const activeThread = state.activeId ? state.threads[state.activeId] : undefined;
  const activeSeq = latestSeq(activeThread);
  const [visible, setVisible] = useState(true);
  useEffect(() => {
    const update = () => setVisible(document.visibilityState !== "hidden");
    update();
    document.addEventListener("visibilitychange", update);
    return () => document.removeEventListener("visibilitychange", update);
  }, []);
  useEffect(() => {
    const conversationId = state.activeId;
    if (!conversationId || !visible || activeSeq <= 0) return;
    if ((lastMarked.current.get(conversationId) ?? 0) >= activeSeq) return;
    lastMarked.current.set(conversationId, activeSeq);
    const timer = setTimeout(() => {
      api.markRead(conversationId, activeSeq).then(
        (marker) => dispatch({ type: "markedRead", conversationId, unreadCount: marker.unreadCount }),
        () => lastMarked.current.delete(conversationId)
      );
    }, 250);
    return () => clearTimeout(timer);
  }, [api, state.activeId, activeSeq, visible]);

  const submit = useCallback(
    (conversationId: ConversationId, clientMessageId: string, input: SendMessageInput) => {
      api.send(conversationId, input).then(
        (message) => dispatch({ type: "sendSucceeded", clientMessageId, message }),
        (error: unknown) =>
          dispatch({ type: "sendFailed", conversationId, clientMessageId, errorKey: messagingErrorKey(error) })
      );
    },
    [api]
  );

  const value = useMemo<MessagingContextValue>(
    () => ({
      state,
      streamStatus,
      api,
      reloadList: () => loadList(false),
      loadMoreConversations: () => loadList(true),
      openThread: (conversationId) => {
        const thread = stateRef.current.threads[conversationId];
        if (!thread) loadThread(conversationId);
        else void resyncThread(conversationId);
      },
      reloadThread: loadThread,
      loadOlder: (conversationId) => {
        const thread = stateRef.current.threads[conversationId];
        if (!thread || thread.loadingOlder || !thread.hasMoreBefore) return;
        const oldest = thread.messages.find((message) => !message.local);
        if (!oldest) return;
        dispatch({ type: "olderLoading", conversationId });
        api.history(conversationId, { before: oldest.seq, limit: HISTORY_PAGE }).then(
          (page) => dispatch({ type: "olderLoaded", conversationId, page }),
          (error: unknown) => dispatch({ type: "olderFailed", conversationId, error })
        );
      },
      setActive: (conversationId) => dispatch({ type: "setActive", conversationId }),
      send: (conversationId, draft) => {
        const message = localMessage(conversationId, meId, draft);
        dispatch({ type: "localSend", message });
        submit(conversationId, message.clientMessageId, message.local!.input);
      },
      retry: (conversationId, clientMessageId) => {
        const message = stateRef.current.threads[conversationId]?.messages.find(
          (item) => item.local && item.clientMessageId === clientMessageId
        );
        if (!message?.local) return;
        dispatch({ type: "localRetry", conversationId, clientMessageId });
        submit(conversationId, clientMessageId, message.local.input);
      },
      discard: (conversationId, clientMessageId) => dispatch({ type: "localDiscard", conversationId, clientMessageId }),
      react: async (conversationId, messageId, emoji) => {
        if (emoji === null) {
          await api.unreact(conversationId, messageId);
          const message = stateRef.current.threads[conversationId]?.messages.find((item) => item.id === messageId);
          if (message?.reactions.mine) {
            const summary = message.reactions.summary
              .map((item) => (item.emoji === message.reactions.mine ? { ...item, count: item.count - 1 } : item))
              .filter((item) => item.count > 0);
            dispatch({ type: "reactionChanged", conversationId, messageId, reactions: { summary, mine: null } });
          }
          return;
        }
        const reactions = await api.react(conversationId, messageId, emoji);
        dispatch({ type: "reactionChanged", conversationId, messageId, reactions });
      },
      remove: async (conversationId, messageId, scope) => {
        await api.remove(conversationId, messageId, scope);
        dispatch({ type: scope === "me" ? "hiddenLocally" : "deletedLocally", conversationId, messageId });
      },
      forward: async (conversationId, messageId, targets) => {
        const result = await api.forward(conversationId, messageId, newClientMessageId(), targets);
        for (const message of result.messages) {
          dispatch({ type: "stream", event: { type: "message", data: message } });
        }
      },
      openDirect: async (userId) => {
        const conversation = await api.openDirect(userId);
        dispatch({ type: "conversationOpened", conversation });
        return conversation;
      },
      createGroup: async (title, memberIds) => {
        const conversation = await api.createGroup(title, memberIds);
        dispatch({ type: "conversationOpened", conversation });
        return conversation;
      },
      conversationChanged: (conversation) => dispatch({ type: "conversationUpdated", conversation }),
      conversationLeft: (conversationId) => dispatch({ type: "conversationRemoved", conversationId })
    }),
    [state, streamStatus, api, loadList, loadThread, resyncThread, submit, meId]
  );

  return <MessagingContext.Provider value={value}>{children}</MessagingContext.Provider>;
}

export function useMessaging(): MessagingContextValue {
  const value = useContext(MessagingContext);
  if (!value) throw new Error("useMessaging yalnız MessagingProvider içinde kullanılabilir.");
  return value;
}
