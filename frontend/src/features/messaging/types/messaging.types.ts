/**
 * Messaging Service tipleri (`docs/api.md` → `## MESSAGING`). Kimlikler UUID, zamanlar ISO-8601 (UTC) metindir.
 * Ad, kullanıcı adı ve avatar messaging-service'te yoktur; user-service toplu profil kartından eklenir.
 */

export type UserId = string;
export type ConversationId = string;
export type MessageId = string;

export type ConversationType = "DIRECT" | "GROUP";
export type MemberRole = "OWNER" | "ADMIN" | "MEMBER";
export type MessageType = "TEXT" | "IMAGE" | "GIF" | "VOICE";
export type ReadStatus = "SENT" | "READ";

/** Hesabı silinmiş kullanıcının mesajlarında dönen anonim gönderen (LTZ-UMT-MSG-07). */
export const DELETED_ACCOUNT_ID = "00000000-0000-0000-0000-000000000000";

/** Okunmamış sayısı sunucuda 100'de durur: `100` "100 ya da daha fazla" demektir. */
export const UNREAD_CAP = 100;

export type ConversationMember = {
  userId: UserId;
  role: MemberRole;
  joinedAt: string;
};

export type Conversation = {
  id: ConversationId;
  type: ConversationType;
  title: string | null;
  createdBy: UserId;
  createdAt: string;
  myRole: MemberRole;
  members: ConversationMember[];
};

export type Attachment = {
  id: string;
  mimeType: string;
  sizeBytes: number;
  width: number | null;
  height: number | null;
  frameCount: number | null;
  durationMs: number | null;
  waveform: number[] | null;
  url: string;
};

export type GifRef = {
  provider: "GIPHY";
  id: string;
  url: string;
  previewUrl: string;
  width: number;
  height: number;
};

export type ReplyRef = {
  messageId: MessageId;
  seq: number | null;
  senderId: UserId | null;
  type: MessageType | null;
  snippet: string | null;
  deleted: boolean;
  unavailable: boolean;
};

export type ReactionCount = { emoji: string; count: number };

export type ReactionSummary = {
  summary: ReactionCount[];
  mine: string | null;
};

export type Message = {
  id: MessageId;
  conversationId: ConversationId;
  seq: number;
  senderId: UserId;
  type: MessageType;
  body: string | null;
  clientMessageId: string;
  createdAt: string;
  readStatus: ReadStatus | null;
  attachment: Attachment | null;
  gif: GifRef | null;
  replyTo: ReplyRef | null;
  forwarded: boolean;
  forwardedManyTimes: boolean;
  deleted: boolean;
  reactions: ReactionSummary;
  deletableForEveryoneUntil: string | null;
};

export type ConversationSummary = {
  id: ConversationId;
  type: ConversationType;
  title: string | null;
  peerUserId: UserId | null;
  memberCount: number;
  myRole: MemberRole;
  lastActivityAt: string;
  lastMessage: Message | null;
  unreadCount: number;
};

export type ConversationPage = {
  items: ConversationSummary[];
  nextCursor: string | null;
};

export type MessagePage = {
  items: Message[];
  hasMore: boolean;
};

export type ReadMarker = {
  conversationId: ConversationId;
  lastReadSeq: number;
  unreadCount: number;
};

export type MessageReads = {
  messageId: MessageId;
  seq: number;
  readStatus: ReadStatus;
  readers: Array<{ userId: UserId; readAt: string | null }>;
  pending: Array<{ userId: UserId }>;
};

export type MessageReactions = {
  messageId: MessageId;
  reactions: Array<{ userId: UserId; emoji: string; reactedAt: string }>;
};

export type GifItem = {
  provider: "GIPHY";
  id: string;
  title: string | null;
  url: string;
  previewUrl: string;
  width: number;
  height: number;
};

export type GifPage = {
  items: GifItem[];
  nextOffset: number | null;
};

/** Gönderim gövdesi; tür sunucuda içerikten belirlenir. */
export type SendMessageInput = {
  clientMessageId: string;
  body?: string;
  attachmentId?: string;
  gif?: { provider: "GIPHY"; id: string };
  replyToMessageId?: MessageId;
};

export type DeleteScope = "me" | "everyone";

// ---- Canlı akış olayları (`GET /api/v1/messaging/stream`) ----

export type ReadEvent = {
  conversationId: ConversationId;
  readerId: UserId | null;
  readUpToSeq: number | null;
  readAt: string | null;
  allReadUpToSeq: number;
};

export type MessageDeletedEvent = {
  conversationId: ConversationId;
  messageId: MessageId;
  seq: number;
  deletedAt: string;
};

export type ReactionEvent = {
  conversationId: ConversationId;
  messageId: MessageId;
  summary: ReactionCount[];
  mine: string | null;
};

export type MessageHiddenEvent = {
  conversationId: ConversationId;
  messageId: MessageId;
};

export type StreamEvent =
  | { type: "message"; data: Message }
  | { type: "read"; data: ReadEvent }
  | { type: "message_deleted"; data: MessageDeletedEvent }
  | { type: "reaction"; data: ReactionEvent }
  | { type: "message_hidden"; data: MessageHiddenEvent };

/** Bağlantı göstergesi (web-rules §16): canlı, yeniden bağlanıyor, kapalı. */
export type StreamStatus = "connecting" | "live" | "reconnecting" | "offline";

// ---- İstemci tarafı görünüm ----

/** Gönderilmekte olan / başarısız yerel mesaj (iyimser gösterim; web-rules §7.2). */
export type LocalState = {
  state: "sending" | "failed";
  /** Başarısızlık nedeni (`Messaging.errors.*` anahtarı). */
  errorKey?: string;
  /** Aynı `clientMessageId` ile yeniden denemek için saklanan gönderim gövdesi. */
  input: SendMessageInput;
};

export type ThreadMessage = Message & { local?: LocalState };
