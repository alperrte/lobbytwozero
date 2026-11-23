/**
 * TEST ÇİFTESİ VERİSİ — yalnız testlerde kullanılır (üretim kodu gerçek servise gider). Kimlikler sabit UUID'dir; gerçek kişisel
 * veri içermez.
 */
import type { Conversation, ConversationSummary, Message } from "../types/messaging.types";

export const ME = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
export const PEER = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
export const OTHER = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
export const DIRECT_ID = "11111111-1111-4111-8111-111111111111";
export const GROUP_ID = "22222222-2222-4222-8222-222222222222";

let counter = 0;

export function message(overrides: Partial<Message> = {}): Message {
  counter += 1;
  const seq = overrides.seq ?? counter;
  return {
    id: overrides.id ?? `00000000-0000-4000-8000-${String(seq).padStart(12, "0")}`,
    conversationId: DIRECT_ID,
    seq,
    senderId: PEER,
    type: "TEXT",
    body: `mesaj ${seq}`,
    clientMessageId: `client-${seq}`,
    createdAt: new Date(Date.UTC(2026, 10, 20, 10, 0, Math.min(seq, 100_000))).toISOString(),
    readStatus: null,
    attachment: null,
    gif: null,
    replyTo: null,
    forwarded: false,
    forwardedManyTimes: false,
    deleted: false,
    reactions: { summary: [], mine: null },
    deletableForEveryoneUntil: null,
    ...overrides
  };
}

export function summary(overrides: Partial<ConversationSummary> = {}): ConversationSummary {
  return {
    id: DIRECT_ID,
    type: "DIRECT",
    title: null,
    peerUserId: PEER,
    memberCount: 2,
    myRole: "MEMBER",
    lastActivityAt: "2026-11-20T10:00:00.000Z",
    lastMessage: null,
    unreadCount: 0,
    ...overrides
  };
}

export function conversation(overrides: Partial<Conversation> = {}): Conversation {
  return {
    id: DIRECT_ID,
    type: "DIRECT",
    title: null,
    createdBy: ME,
    createdAt: "2026-11-20T09:00:00.000Z",
    myRole: "MEMBER",
    members: [
      { userId: ME, role: "MEMBER", joinedAt: "2026-11-20T09:00:00.000Z" },
      { userId: PEER, role: "MEMBER", joinedAt: "2026-11-20T09:00:00.000Z" }
    ],
    ...overrides
  };
}
