import { describe, expect, it } from "vitest";
import { DIRECT_ID, GROUP_ID, ME, PEER, conversation, message, summary } from "../services/messaging-mock-data";
import type { ThreadMessage } from "../types/messaging.types";
import { initialMessagingState, latestSeq, messagingReducer, type MessagingAction, type MessagingState } from "./messaging-reducer";

function run(state: MessagingState, ...actions: MessagingAction[]): MessagingState {
  return actions.reduce(messagingReducer, state);
}

function withThread(messages = [message({ seq: 1 }), message({ seq: 2 })]) {
  return run(
    initialMessagingState(ME),
    { type: "listLoaded", page: { items: [summary({ lastMessage: messages.at(-1) ?? null })], nextCursor: null }, append: false },
    { type: "threadLoading", conversationId: DIRECT_ID },
    { type: "threadLoaded", conversationId: DIRECT_ID, page: { items: [...messages].reverse(), hasMore: true } }
  );
}

const seqs = (state: MessagingState) => state.threads[DIRECT_ID].messages.map((item) => (item.local ? "local" : item.seq));

describe("messagingReducer", () => {
  it("geçmişi seq'e göre artan sıralar; eski sayfa başa eklenir, tekrar eden mesaj iki kez eklenmez", () => {
    let state = withThread([message({ seq: 5 }), message({ seq: 6 })]);
    expect(seqs(state)).toEqual([5, 6]);
    state = run(state, { type: "olderLoaded", conversationId: DIRECT_ID, page: { items: [message({ seq: 4 }), message({ seq: 3 })], hasMore: false } });
    expect(seqs(state)).toEqual([3, 4, 5, 6]);
    expect(state.threads[DIRECT_ID].hasMoreBefore).toBe(false);
    state = run(state, { type: "stream", event: { type: "message", data: message({ seq: 6 }) } });
    expect(seqs(state)).toEqual([3, 4, 5, 6]);
  });

  it("iyimser gönderim: yerel mesaj sonda durur, sunucu kaydı gelince clientMessageId ile yerine geçer", () => {
    const local: ThreadMessage = {
      ...message({ id: "local:x", seq: Number.MAX_SAFE_INTEGER, senderId: ME, clientMessageId: "x" }),
      local: { state: "sending", input: { clientMessageId: "x", body: "selam" } }
    };
    let state = run(withThread(), { type: "localSend", message: local });
    expect(seqs(state)).toEqual([1, 2, "local"]);
    state = run(state, { type: "sendFailed", conversationId: DIRECT_ID, clientMessageId: "x", errorKey: "network" });
    expect(state.threads[DIRECT_ID].messages.at(-1)?.local).toMatchObject({ state: "failed", errorKey: "network" });
    state = run(state, { type: "localRetry", conversationId: DIRECT_ID, clientMessageId: "x" });
    expect(state.threads[DIRECT_ID].messages.at(-1)?.local?.state).toBe("sending");
    // Akış olayı HTTP yanıtından önce gelebilir: aynı istemci kimliği yerel kopyayı kaldırır, yanıt ikinci kez eklemez.
    const saved = message({ seq: 3, senderId: ME, clientMessageId: "x", readStatus: "SENT" });
    state = run(state, { type: "stream", event: { type: "message", data: saved } }, { type: "sendSucceeded", clientMessageId: "x", message: saved });
    expect(seqs(state)).toEqual([1, 2, 3]);
    expect(state.conversations[0].lastMessage?.id).toBe(saved.id);
    expect(state.conversations[0].unreadCount).toBe(0);
  });

  it("başkasından gelen mesaj okunmamışı artırır (100'de durur); açık sohbette artırmaz", () => {
    let state = run(withThread(), { type: "listLoaded", page: { items: [summary({ unreadCount: 99 })], nextCursor: null }, append: false });
    state = run(state, { type: "stream", event: { type: "message", data: message({ seq: 10 }) } });
    expect(state.conversations[0].unreadCount).toBe(100);
    state = run(state, { type: "stream", event: { type: "message", data: message({ seq: 11 }) } });
    expect(state.conversations[0].unreadCount).toBe(100);
    state = run(state, { type: "setActive", conversationId: DIRECT_ID }, { type: "markedRead", conversationId: DIRECT_ID, unreadCount: 0 });
    state = run(state, { type: "stream", event: { type: "message", data: message({ seq: 12 }) } });
    expect(state.conversations[0].unreadCount).toBe(0);
  });

  it("listede olmayan sohbetten mesaj gelince liste yenilenmek üzere işaretlenir", () => {
    const state = run(withThread(), { type: "stream", event: { type: "message", data: message({ conversationId: GROUP_ID, seq: 1 }) } });
    expect(state.listStale).toBe(true);
  });

  it("son etkinliğe göre sıralar", () => {
    let state = run(initialMessagingState(ME), {
      type: "listLoaded",
      page: {
        items: [summary({ id: DIRECT_ID, lastActivityAt: "2026-11-20T12:00:00Z" }), summary({ id: GROUP_ID, type: "GROUP", peerUserId: null, lastActivityAt: "2026-11-20T11:00:00Z" })],
        nextCursor: "n"
      },
      append: false
    });
    state = run(state, { type: "stream", event: { type: "message", data: message({ conversationId: GROUP_ID, seq: 1, createdAt: "2026-11-20T13:00:00Z" }) } });
    expect(state.conversations.map((item) => item.id)).toEqual([GROUP_ID, DIRECT_ID]);
  });

  it("read olayı kendi mesajlarından allReadUpToSeq'e kadar olanları READ yapar", () => {
    let state = withThread([
      message({ seq: 1, senderId: ME, readStatus: "SENT" }),
      message({ seq: 2, senderId: ME, readStatus: "SENT" }),
      message({ seq: 3, senderId: PEER })
    ]);
    state = run(state, {
      type: "stream",
      event: { type: "read", data: { conversationId: DIRECT_ID, readerId: PEER, readUpToSeq: 1, readAt: null, allReadUpToSeq: 1 } }
    });
    expect(state.threads[DIRECT_ID].messages.map((item) => item.readStatus)).toEqual(["READ", "SENT", null]);
  });

  it("herkesten silme içeriği temizler, yanıt alıntısını silinmiş yapar; benden silme mesajı kaldırır", () => {
    const target = message({ seq: 1, reactions: { summary: [{ emoji: "❤️", count: 1 }], mine: "❤️" } });
    const reply = message({ seq: 2, replyTo: { messageId: target.id, seq: 1, senderId: PEER, type: "TEXT", snippet: "mesaj 1", deleted: false, unavailable: false } });
    let state = withThread([target, reply]);
    state = run(state, {
      type: "stream",
      event: { type: "message_deleted", data: { conversationId: DIRECT_ID, messageId: target.id, seq: 1, deletedAt: "2026-11-20T10:05:00Z" } }
    });
    const [deleted, quoted] = state.threads[DIRECT_ID].messages;
    expect(deleted).toMatchObject({ deleted: true, body: null, reactions: { summary: [], mine: null } });
    expect(quoted.replyTo).toMatchObject({ deleted: true, snippet: null });

    state = run(state, { type: "stream", event: { type: "message_hidden", data: { conversationId: DIRECT_ID, messageId: reply.id } } });
    expect(seqs(state)).toEqual([1]);
    expect(state.conversations[0].lastMessage?.id).toBe(target.id);
  });

  it("tepki olayı özeti günceller; silinmiş mesaja dokunmaz", () => {
    const target = message({ seq: 1 });
    let state = withThread([target]);
    state = run(state, {
      type: "stream",
      event: { type: "reaction", data: { conversationId: DIRECT_ID, messageId: target.id, summary: [{ emoji: "👍", count: 2 }], mine: "👍" } }
    });
    expect(state.threads[DIRECT_ID].messages[0].reactions).toEqual({ summary: [{ emoji: "👍", count: 2 }], mine: "👍" });
  });

  it("yeni açılan sohbet listeye eklenir, ayrılınca kaldırılır", () => {
    let state = run(initialMessagingState(ME), { type: "listLoaded", page: { items: [], nextCursor: null }, append: false });
    state = run(state, { type: "conversationOpened", conversation: conversation() });
    expect(state.conversations[0]).toMatchObject({ id: DIRECT_ID, peerUserId: PEER, lastMessage: null });
    state = run(state, { type: "conversationRemoved", conversationId: DIRECT_ID });
    expect(state.conversations).toEqual([]);
  });

  it("latestSeq yerel mesajları saymaz", () => {
    const state = withThread([message({ seq: 4 })]);
    expect(latestSeq(state.threads[DIRECT_ID])).toBe(4);
    expect(latestSeq(undefined)).toBe(0);
  });
});
