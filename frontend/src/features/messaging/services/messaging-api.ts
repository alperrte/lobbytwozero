import type {
  Attachment,
  Conversation,
  ConversationId,
  ConversationPage,
  DeleteScope,
  GifPage,
  MemberRole,
  Message,
  MessageId,
  MessagePage,
  MessageReactions,
  MessageReads,
  ReactionSummary,
  ReadMarker,
  SendMessageInput,
  UserId
} from "../types/messaging.types";
import { messagingRequest, uploadRequest } from "./messaging-http";

/**
 * messaging-service istemcisi (`docs/api.md` → `## MESSAGING`). Tüm yollar Gateway'in `/api/v1/messaging/**` önekindedir.
 * Kimlik yalnız oturum çerezinden (Gateway → Bearer) gelir; gövdeye gönderen kimliği yazılmaz (sunucu tanımsız alanı `400`
 * ile reddeder). Path parametreleri `encodeURIComponent` ile kaçırılır.
 */
const BASE = "/api/v1/messaging";

const id = (value: string) => encodeURIComponent(value);

function withQuery(path: string, query: Record<string, string | number | undefined>): `/${string}` {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== "") {
      params.set(key, String(value));
    }
  }
  const search = params.toString();
  return `${BASE}${path}${search ? `?${search}` : ""}` as `/${string}`;
}

const conversation = (conversationId: ConversationId) => `/conversations/${id(conversationId)}`;
const message = (conversationId: ConversationId, messageId: MessageId) =>
  `${conversation(conversationId)}/messages/${id(messageId)}`;

export type HistoryQuery = { before?: number; after?: number; limit?: number };

export const messagingApi = {
  listConversations(query: { limit?: number; cursor?: string | null } = {}, signal?: AbortSignal) {
    return messagingRequest<ConversationPage>(
      withQuery("/conversations", { limit: query.limit, cursor: query.cursor ?? undefined }),
      { signal }
    );
  },

  getConversation(conversationId: ConversationId, signal?: AbortSignal) {
    return messagingRequest<Conversation>(`${BASE}${conversation(conversationId)}`, { signal });
  },

  /** Varsa mevcut sohbet (200), yoksa yenisi (201) döner. */
  openDirect(userId: UserId) {
    return messagingRequest<Conversation>(`${BASE}/conversations/direct`, { method: "POST", body: { userId } });
  },

  createGroup(title: string, memberIds: UserId[]) {
    return messagingRequest<Conversation>(`${BASE}/conversations/groups`, {
      method: "POST",
      body: { title, memberIds }
    });
  },

  addMembers(conversationId: ConversationId, userIds: UserId[]) {
    return messagingRequest<Conversation>(`${BASE}${conversation(conversationId)}/members`, {
      method: "POST",
      body: { userIds }
    });
  },

  removeMember(conversationId: ConversationId, userId: UserId) {
    return messagingRequest<void>(`${BASE}${conversation(conversationId)}/members/${id(userId)}`, { method: "DELETE" });
  },

  changeRole(conversationId: ConversationId, userId: UserId, role: Exclude<MemberRole, "OWNER">) {
    return messagingRequest<Conversation>(`${BASE}${conversation(conversationId)}/members/${id(userId)}/role`, {
      method: "PUT",
      body: { role }
    });
  },

  leave(conversationId: ConversationId) {
    return messagingRequest<void>(`${BASE}${conversation(conversationId)}/leave`, { method: "POST" });
  },

  history(conversationId: ConversationId, query: HistoryQuery = {}, signal?: AbortSignal) {
    return messagingRequest<MessagePage>(withQuery(`${conversation(conversationId)}/messages`, query), { signal });
  },

  send(conversationId: ConversationId, input: SendMessageInput) {
    return messagingRequest<Message>(`${BASE}${conversation(conversationId)}/messages`, { method: "POST", body: input });
  },

  markRead(conversationId: ConversationId, seq: number) {
    return messagingRequest<ReadMarker>(`${BASE}${conversation(conversationId)}/read`, { method: "POST", body: { seq } });
  },

  reads(conversationId: ConversationId, messageId: MessageId, signal?: AbortSignal) {
    return messagingRequest<MessageReads>(`${BASE}${message(conversationId, messageId)}/reads`, { signal });
  },

  reactions(conversationId: ConversationId, messageId: MessageId, signal?: AbortSignal) {
    return messagingRequest<MessageReactions>(`${BASE}${message(conversationId, messageId)}/reactions`, { signal });
  },

  react(conversationId: ConversationId, messageId: MessageId, emoji: string) {
    return messagingRequest<ReactionSummary>(`${BASE}${message(conversationId, messageId)}/reaction`, {
      method: "PUT",
      body: { emoji }
    });
  },

  unreact(conversationId: ConversationId, messageId: MessageId) {
    return messagingRequest<void>(`${BASE}${message(conversationId, messageId)}/reaction`, { method: "DELETE" });
  },

  remove(conversationId: ConversationId, messageId: MessageId, scope: DeleteScope) {
    return messagingRequest<void>(withQuery(message(conversationId, messageId), { scope }), { method: "DELETE" });
  },

  forward(conversationId: ConversationId, messageId: MessageId, clientMessageId: string, targetConversationIds: ConversationId[]) {
    return messagingRequest<{ messages: Message[] }>(`${BASE}${message(conversationId, messageId)}/forward`, {
      method: "POST",
      body: { clientMessageId, targetConversationIds }
    });
  },

  upload(conversationId: ConversationId, file: Blob, onProgress: (fraction: number) => void) {
    return uploadRequest<Attachment>(`${BASE}${conversation(conversationId)}/attachments`, file, onProgress);
  },

  gifTrending(query: { limit?: number; offset?: number } = {}, signal?: AbortSignal) {
    return messagingRequest<GifPage>(withQuery("/gifs/trending", query), { signal });
  },

  gifSearch(query: { q: string; limit?: number; offset?: number; lang?: "tr" | "en" }, signal?: AbortSignal) {
    return messagingRequest<GifPage>(withQuery("/gifs/search", query), { signal });
  }
};

export type MessagingSource = typeof messagingApi;
