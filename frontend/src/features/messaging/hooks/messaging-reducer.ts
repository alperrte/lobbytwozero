import {
  UNREAD_CAP,
  type Conversation,
  type ConversationId,
  type ConversationPage,
  type ConversationSummary,
  type Message,
  type MessageId,
  type MessagePage,
  type ReactionSummary,
  type StreamEvent,
  type ThreadMessage,
  type UserId
} from "../types/messaging.types";

/**
 * Mesajlaşma ekranının tek durum kaynağı: sohbet listesi, açılmış sohbetlerin mesajları ve canlı akış olayları.
 * Saf fonksiyondur (yan etki yok); istekler hook'tadır. Kurallar `docs/api.md` → MESSAGING:
 * - Mesajlar `seq`'e göre artan sıradadır; aynı mesaj (aynı `id`) iki kez eklenmez. Yerel (gönderiliyor/başarısız) mesajlar
 *   en sondadır ve sunucu yanıtı ya da akış olayı geldiğinde `clientMessageId` ile yerini sunucu kaydına bırakır.
 * - Okunmamış sayısı 100'de durur (`99+`); açık ve görünür sohbete gelen mesaj okunmamış sayılmaz (istemci hemen okur).
 * - `read` olayında kendi mesajlarından `seq <= allReadUpToSeq` olanlar `READ` olur.
 * - `message_deleted` içeriği temizler ("Bu mesaj silindi"), ona verilen yanıtların alıntısını da silinmiş yapar.
 * - `message_hidden` (benden sil) mesajı yalnız bu kullanıcının görünümünden kaldırır.
 */

export type LoadStatus = "loading" | "ready" | "error";

export type ThreadState = {
  messages: ThreadMessage[];
  hasMoreBefore: boolean;
  status: LoadStatus;
  error?: unknown;
  loadingOlder: boolean;
  olderError?: unknown;
};

export type MessagingState = {
  meId: UserId;
  conversations: ConversationSummary[];
  nextCursor: string | null;
  listStatus: LoadStatus;
  listError?: unknown;
  /** Liste sunucudan yeniden okunmalı (bilinmeyen sohbetten mesaj geldi ya da son mesaj kalktı). */
  listStale: boolean;
  threads: Record<ConversationId, ThreadState>;
  /** Ekranda açık ve görünür sohbet (okunmamış sayımı için). */
  activeId: ConversationId | null;
};

export type MessagingAction =
  | { type: "listLoading" }
  | { type: "listLoaded"; page: ConversationPage; append: boolean }
  | { type: "listFailed"; error: unknown }
  | { type: "conversationOpened"; conversation: Conversation }
  | { type: "conversationUpdated"; conversation: Conversation }
  | { type: "conversationRemoved"; conversationId: ConversationId }
  | { type: "setActive"; conversationId: ConversationId | null }
  | { type: "threadLoading"; conversationId: ConversationId }
  | { type: "threadLoaded"; conversationId: ConversationId; page: MessagePage }
  | { type: "threadFailed"; conversationId: ConversationId; error: unknown }
  | { type: "olderLoading"; conversationId: ConversationId }
  | { type: "olderLoaded"; conversationId: ConversationId; page: MessagePage }
  | { type: "olderFailed"; conversationId: ConversationId; error: unknown }
  | { type: "threadSynced"; conversationId: ConversationId; messages: Message[] }
  | { type: "localSend"; message: ThreadMessage }
  | { type: "sendSucceeded"; clientMessageId: string; message: Message }
  | { type: "sendFailed"; conversationId: ConversationId; clientMessageId: string; errorKey: string }
  | { type: "localRetry"; conversationId: ConversationId; clientMessageId: string }
  | { type: "localDiscard"; conversationId: ConversationId; clientMessageId: string }
  | { type: "markedRead"; conversationId: ConversationId; unreadCount: number }
  | { type: "reactionChanged"; conversationId: ConversationId; messageId: MessageId; reactions: ReactionSummary }
  | { type: "hiddenLocally"; conversationId: ConversationId; messageId: MessageId }
  | { type: "deletedLocally"; conversationId: ConversationId; messageId: MessageId }
  | { type: "stream"; event: StreamEvent };

export function initialMessagingState(meId: UserId): MessagingState {
  return {
    meId,
    conversations: [],
    nextCursor: null,
    listStatus: "loading",
    listStale: false,
    threads: {},
    activeId: null
  };
}

const EMPTY_REACTIONS: ReactionSummary = { summary: [], mine: null };

function byActivity(a: ConversationSummary, b: ConversationSummary): number {
  const diff = Date.parse(b.lastActivityAt) - Date.parse(a.lastActivityAt);
  if (diff !== 0) return diff;
  return a.id < b.id ? 1 : a.id > b.id ? -1 : 0;
}

/** Sunucu kayıtlarını `seq`'e göre sıralar; yerel (henüz `seq`'i olmayan) mesajlar sonda kalır. */
function sortMessages(messages: ThreadMessage[]): ThreadMessage[] {
  const server = messages.filter((m) => !m.local).sort((a, b) => a.seq - b.seq);
  const local = messages.filter((m) => m.local);
  return [...server, ...local];
}

function mergeMessages(existing: ThreadMessage[], incoming: Message[]): ThreadMessage[] {
  if (incoming.length === 0) return existing;
  const byId = new Map<MessageId, ThreadMessage>();
  const locals: ThreadMessage[] = [];
  for (const message of existing) {
    if (message.local) locals.push(message);
    else byId.set(message.id, message);
  }
  const arrivedClientIds = new Set<string>();
  for (const message of incoming) {
    byId.set(message.id, message);
    arrivedClientIds.add(message.clientMessageId);
  }
  const keptLocals = locals.filter((local) => !arrivedClientIds.has(local.clientMessageId));
  return sortMessages([...byId.values(), ...keptLocals]);
}

function updateThread(
  state: MessagingState,
  conversationId: ConversationId,
  update: (thread: ThreadState) => ThreadState
): MessagingState {
  const thread = state.threads[conversationId];
  if (!thread) return state;
  return { ...state, threads: { ...state.threads, [conversationId]: update(thread) } };
}

function updateSummary(
  state: MessagingState,
  conversationId: ConversationId,
  update: (summary: ConversationSummary) => ConversationSummary
): MessagingState {
  let changed = false;
  const conversations = state.conversations.map((summary) => {
    if (summary.id !== conversationId) return summary;
    changed = true;
    return update(summary);
  });
  return changed ? { ...state, conversations } : state;
}

function mapMessage(
  messages: ThreadMessage[],
  messageId: MessageId,
  update: (message: ThreadMessage) => ThreadMessage
): ThreadMessage[] {
  return messages.map((message) => (message.id === messageId ? update(message) : message));
}

function wipe(message: Message): Message {
  return {
    ...message,
    deleted: true,
    body: null,
    attachment: null,
    gif: null,
    replyTo: null,
    reactions: EMPTY_REACTIONS,
    deletableForEveryoneUntil: null
  };
}

function summaryFrom(conversation: Conversation, meId: UserId): ConversationSummary {
  return {
    id: conversation.id,
    type: conversation.type,
    title: conversation.title,
    peerUserId:
      conversation.type === "DIRECT" ? conversation.members.find((member) => member.userId !== meId)?.userId ?? null : null,
    memberCount: conversation.members.length,
    myRole: conversation.myRole,
    lastActivityAt: conversation.createdAt,
    lastMessage: null,
    unreadCount: 0
  };
}

function lastServerMessage(thread: ThreadState | undefined): Message | null {
  if (!thread) return null;
  for (let index = thread.messages.length - 1; index >= 0; index -= 1) {
    const message = thread.messages[index];
    if (!message.local) return message;
  }
  return null;
}

/** Yeni mesaj: açık sohbetin geçmişine ekle, listedeki özeti güncelle ve listeyi en son etkinliğe göre sırala. */
function applyMessage(state: MessagingState, message: Message): MessagingState {
  let next = updateThread(state, message.conversationId, (thread) => ({
    ...thread,
    messages: mergeMessages(thread.messages, [message])
  }));
  const known = next.conversations.some((summary) => summary.id === message.conversationId);
  if (!known) {
    return { ...next, listStale: true };
  }
  next = updateSummary(next, message.conversationId, (summary) => {
    const newer = !summary.lastMessage || summary.lastMessage.seq <= message.seq;
    if (!newer) return summary;
    const counts = message.senderId !== state.meId && state.activeId !== message.conversationId;
    const isRepeat = summary.lastMessage?.id === message.id;
    return {
      ...summary,
      lastMessage: message,
      lastActivityAt: message.createdAt,
      unreadCount: counts && !isRepeat ? Math.min(UNREAD_CAP, summary.unreadCount + 1) : summary.unreadCount
    };
  });
  return { ...next, conversations: [...next.conversations].sort(byActivity) };
}

function applyStream(state: MessagingState, event: StreamEvent): MessagingState {
  switch (event.type) {
    case "message":
      return applyMessage(state, event.data);
    case "read": {
      const { conversationId, allReadUpToSeq } = event.data;
      const markRead = <T extends Message>(message: T): T =>
        message.senderId === state.meId && message.seq <= allReadUpToSeq && message.readStatus !== "READ"
          ? { ...message, readStatus: "READ" }
          : message;
      const next = updateThread(state, conversationId, (thread) => ({
        ...thread,
        messages: thread.messages.map((message) => (message.local ? message : markRead(message)))
      }));
      return updateSummary(next, conversationId, (summary) =>
        summary.lastMessage ? { ...summary, lastMessage: markRead(summary.lastMessage) } : summary
      );
    }
    case "message_deleted":
      return applyDeleted(state, event.data.conversationId, event.data.messageId);
    case "reaction": {
      const reactions = { summary: event.data.summary, mine: event.data.mine };
      return applyReactions(state, event.data.conversationId, event.data.messageId, reactions);
    }
    case "message_hidden":
      return applyHidden(state, event.data.conversationId, event.data.messageId);
  }
}

function applyDeleted(state: MessagingState, conversationId: ConversationId, messageId: MessageId): MessagingState {
  const next = updateThread(state, conversationId, (thread) => ({
    ...thread,
    messages: thread.messages.map((message) => {
      if (message.id === messageId) return wipe(message);
      if (message.replyTo?.messageId === messageId) {
        return { ...message, replyTo: { ...message.replyTo, deleted: true, snippet: null } };
      }
      return message;
    })
  }));
  return updateSummary(next, conversationId, (summary) =>
    summary.lastMessage?.id === messageId ? { ...summary, lastMessage: wipe(summary.lastMessage) } : summary
  );
}

function applyReactions(
  state: MessagingState,
  conversationId: ConversationId,
  messageId: MessageId,
  reactions: ReactionSummary
): MessagingState {
  const next = updateThread(state, conversationId, (thread) => ({
    ...thread,
    messages: mapMessage(thread.messages, messageId, (message) => (message.deleted ? message : { ...message, reactions }))
  }));
  return updateSummary(next, conversationId, (summary) =>
    summary.lastMessage?.id === messageId ? { ...summary, lastMessage: { ...summary.lastMessage, reactions } } : summary
  );
}

function applyHidden(state: MessagingState, conversationId: ConversationId, messageId: MessageId): MessagingState {
  let next = updateThread(state, conversationId, (thread) => ({
    ...thread,
    messages: thread.messages.filter((message) => message.id !== messageId)
  }));
  const summary = next.conversations.find((item) => item.id === conversationId);
  if (summary?.lastMessage?.id === messageId) {
    const thread = next.threads[conversationId];
    next = updateSummary(next, conversationId, (item) => ({ ...item, lastMessage: lastServerMessage(thread) }));
    if (!thread) next = { ...next, listStale: true };
  }
  return next;
}

export function messagingReducer(state: MessagingState, action: MessagingAction): MessagingState {
  switch (action.type) {
    case "listLoading":
      return { ...state, listStatus: state.conversations.length > 0 ? state.listStatus : "loading", listError: undefined };
    case "listLoaded": {
      const incoming = action.page.items;
      let conversations: ConversationSummary[];
      if (action.append) {
        const seen = new Set(state.conversations.map((item) => item.id));
        conversations = [...state.conversations, ...incoming.filter((item) => !seen.has(item.id))];
      } else {
        // Yenilemede açık sohbet okunmuş sayılır (sunucu yanıtı okumadan önce kurulmuş olabilir).
        conversations = incoming.map((item) => (item.id === state.activeId ? { ...item, unreadCount: 0 } : item));
        // İlk sayfada olmayan ama yerelde açılmış (yeni, mesajsız) sohbetler korunur.
        const ids = new Set(conversations.map((item) => item.id));
        const keep = state.conversations.filter((item) => !ids.has(item.id) && item.id === state.activeId);
        conversations = [...conversations, ...keep].sort(byActivity);
      }
      return {
        ...state,
        conversations,
        nextCursor: action.page.nextCursor,
        listStatus: "ready",
        listError: undefined,
        listStale: false
      };
    }
    case "listFailed":
      return { ...state, listStatus: state.conversations.length > 0 ? "ready" : "error", listError: action.error };
    case "conversationOpened": {
      const exists = state.conversations.some((item) => item.id === action.conversation.id);
      if (exists) return state;
      return {
        ...state,
        conversations: [summaryFrom(action.conversation, state.meId), ...state.conversations].sort(byActivity)
      };
    }
    case "conversationUpdated":
      return updateSummary(state, action.conversation.id, (summary) => ({
        ...summary,
        title: action.conversation.title,
        memberCount: action.conversation.members.length,
        myRole: action.conversation.myRole
      }));
    case "conversationRemoved": {
      const threads = { ...state.threads };
      delete threads[action.conversationId];
      return {
        ...state,
        conversations: state.conversations.filter((item) => item.id !== action.conversationId),
        threads,
        activeId: state.activeId === action.conversationId ? null : state.activeId
      };
    }
    case "setActive":
      return { ...state, activeId: action.conversationId };
    case "threadLoading": {
      const existing = state.threads[action.conversationId];
      return {
        ...state,
        threads: {
          ...state.threads,
          [action.conversationId]: existing
            ? { ...existing, error: undefined }
            : { messages: [], hasMoreBefore: false, status: "loading", loadingOlder: false }
        }
      };
    }
    case "threadLoaded": {
      const existing = state.threads[action.conversationId];
      const locals = existing?.messages.filter((message) => message.local) ?? [];
      // İlk sayfa (en son mesajlar) mevcut yüklü eski mesajların yerine geçer; yerel mesajlar korunur.
      const older = existing?.messages.filter(
        (message) => !message.local && action.page.items.length > 0 && message.seq < Math.min(...action.page.items.map((m) => m.seq))
      ) ?? [];
      return {
        ...state,
        threads: {
          ...state.threads,
          [action.conversationId]: {
            messages: mergeMessages([...older, ...locals], action.page.items),
            hasMoreBefore: older.length > 0 ? existing?.hasMoreBefore ?? action.page.hasMore : action.page.hasMore,
            status: "ready",
            loadingOlder: false
          }
        }
      };
    }
    case "threadFailed":
      return updateThread(state, action.conversationId, (thread) => ({
        ...thread,
        status: thread.messages.length > 0 ? "ready" : "error",
        error: action.error
      }));
    case "olderLoading":
      return updateThread(state, action.conversationId, (thread) => ({ ...thread, loadingOlder: true, olderError: undefined }));
    case "olderLoaded":
      return updateThread(state, action.conversationId, (thread) => ({
        ...thread,
        messages: mergeMessages(thread.messages, action.page.items),
        hasMoreBefore: action.page.hasMore,
        loadingOlder: false
      }));
    case "olderFailed":
      return updateThread(state, action.conversationId, (thread) => ({ ...thread, loadingOlder: false, olderError: action.error }));
    case "threadSynced": {
      let next = updateThread(state, action.conversationId, (thread) => ({
        ...thread,
        messages: mergeMessages(thread.messages, action.messages)
      }));
      const latest = action.messages.reduce<Message | null>((max, m) => (!max || m.seq > max.seq ? m : max), null);
      if (latest) {
        next = updateSummary(next, action.conversationId, (summary) =>
          !summary.lastMessage || summary.lastMessage.seq < latest.seq
            ? { ...summary, lastMessage: latest, lastActivityAt: latest.createdAt }
            : summary
        );
        next = { ...next, conversations: [...next.conversations].sort(byActivity) };
      }
      return next;
    }
    case "localSend":
      return updateThread(state, action.message.conversationId, (thread) => ({
        ...thread,
        messages: [...thread.messages, action.message]
      }));
    case "sendSucceeded":
      return applyMessage(state, action.message);
    case "sendFailed":
      return updateThread(state, action.conversationId, (thread) => ({
        ...thread,
        messages: thread.messages.map((message) =>
          message.local && message.clientMessageId === action.clientMessageId
            ? { ...message, local: { ...message.local, state: "failed", errorKey: action.errorKey } }
            : message
        )
      }));
    case "localRetry":
      return updateThread(state, action.conversationId, (thread) => ({
        ...thread,
        messages: thread.messages.map((message) =>
          message.local && message.clientMessageId === action.clientMessageId
            ? { ...message, local: { ...message.local, state: "sending", errorKey: undefined } }
            : message
        )
      }));
    case "localDiscard":
      return updateThread(state, action.conversationId, (thread) => ({
        ...thread,
        messages: thread.messages.filter((message) => !(message.local && message.clientMessageId === action.clientMessageId))
      }));
    case "markedRead":
      return updateSummary(state, action.conversationId, (summary) => ({ ...summary, unreadCount: action.unreadCount }));
    case "reactionChanged":
      return applyReactions(state, action.conversationId, action.messageId, action.reactions);
    case "hiddenLocally":
      return applyHidden(state, action.conversationId, action.messageId);
    case "deletedLocally":
      return applyDeleted(state, action.conversationId, action.messageId);
    case "stream":
      return applyStream(state, action.event);
  }
}

/** Sohbetteki en büyük sunucu `seq`'i (okundu işaretleme ve `after` eşitlemesi için). */
export function latestSeq(thread: ThreadState | undefined): number {
  if (!thread) return 0;
  let max = 0;
  for (const message of thread.messages) {
    if (!message.local && message.seq > max) max = message.seq;
  }
  return max;
}
