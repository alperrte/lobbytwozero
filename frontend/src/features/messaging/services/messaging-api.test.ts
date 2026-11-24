import { beforeEach, describe, expect, it, vi } from "vitest";

const messagingRequest = vi.fn();
const uploadRequest = vi.fn();
vi.mock("./messaging-http", () => ({
  messagingRequest: (...args: unknown[]) => messagingRequest(...args),
  uploadRequest: (...args: unknown[]) => uploadRequest(...args)
}));

const { messagingApi } = await import("./messaging-api");

const C = "11111111-1111-4111-8111-111111111111";
const M = "22222222-2222-4222-8222-222222222222";
const U = "33333333-3333-4333-8333-333333333333";

beforeEach(() => {
  messagingRequest.mockReset();
  messagingRequest.mockResolvedValue(undefined);
  uploadRequest.mockReset();
});

const calls = () => messagingRequest.mock.calls.map(([path, options]) => `${options?.method ?? "GET"} ${path}`);

describe("messagingApi uç eşlemesi (docs/api.md → MESSAGING)", () => {
  it("liste ve geçmiş uçları sayfalama parametrelerini sorguya yazar", async () => {
    await messagingApi.listConversations({ limit: 20, cursor: "a+b/=" });
    await messagingApi.listConversations();
    await messagingApi.history(C, { before: 40, limit: 30 });
    await messagingApi.history(C, { after: 7 });
    expect(calls()).toEqual([
      "GET /api/v1/messaging/conversations?limit=20&cursor=a%2Bb%2F%3D",
      "GET /api/v1/messaging/conversations",
      `GET /api/v1/messaging/conversations/${C}/messages?before=40&limit=30`,
      `GET /api/v1/messaging/conversations/${C}/messages?after=7`
    ]);
  });

  it("sohbet ve grup yönetimi uçları doğru yöntem ve gövdeyle gider", async () => {
    await messagingApi.openDirect(U);
    await messagingApi.createGroup("Takım", [U]);
    await messagingApi.addMembers(C, [U]);
    await messagingApi.removeMember(C, U);
    await messagingApi.changeRole(C, U, "ADMIN");
    await messagingApi.leave(C);
    await messagingApi.getConversation(C);
    expect(calls()).toEqual([
      "POST /api/v1/messaging/conversations/direct",
      "POST /api/v1/messaging/conversations/groups",
      `POST /api/v1/messaging/conversations/${C}/members`,
      `DELETE /api/v1/messaging/conversations/${C}/members/${U}`,
      `PUT /api/v1/messaging/conversations/${C}/members/${U}/role`,
      `POST /api/v1/messaging/conversations/${C}/leave`,
      `GET /api/v1/messaging/conversations/${C}`
    ]);
    expect(messagingRequest.mock.calls[0][1].body).toEqual({ userId: U });
    expect(messagingRequest.mock.calls[1][1].body).toEqual({ title: "Takım", memberIds: [U] });
    expect(messagingRequest.mock.calls[4][1].body).toEqual({ role: "ADMIN" });
  });

  it("mesaj işlemleri: gönder, okundu, tepki, sil, ilet, okuyanlar", async () => {
    await messagingApi.send(C, { clientMessageId: "c1", body: "selam" });
    await messagingApi.markRead(C, 9);
    await messagingApi.react(C, M, "❤️");
    await messagingApi.unreact(C, M);
    await messagingApi.remove(C, M, "everyone");
    await messagingApi.remove(C, M, "me");
    await messagingApi.forward(C, M, "f1", [C]);
    await messagingApi.reads(C, M);
    await messagingApi.reactions(C, M);
    const base = `/api/v1/messaging/conversations/${C}`;
    expect(calls()).toEqual([
      `POST ${base}/messages`,
      `POST ${base}/read`,
      `PUT ${base}/messages/${M}/reaction`,
      `DELETE ${base}/messages/${M}/reaction`,
      `DELETE ${base}/messages/${M}?scope=everyone`,
      `DELETE ${base}/messages/${M}?scope=me`,
      `POST ${base}/messages/${M}/forward`,
      `GET ${base}/messages/${M}/reads`,
      `GET ${base}/messages/${M}/reactions`
    ]);
    // Gönderen kimliği gövdeye hiç yazılmaz (sunucu tanımsız alanı 400 ile reddeder).
    expect(messagingRequest.mock.calls[0][1].body).toEqual({ clientMessageId: "c1", body: "selam" });
    expect(messagingRequest.mock.calls[1][1].body).toEqual({ seq: 9 });
    expect(messagingRequest.mock.calls[2][1].body).toEqual({ emoji: "❤️" });
    expect(messagingRequest.mock.calls[6][1].body).toEqual({ clientMessageId: "f1", targetConversationIds: [C] });
  });

  it("yol parametrelerini kaçırır", async () => {
    await messagingApi.getConversation("../internal");
    expect(calls()).toEqual(["GET /api/v1/messaging/conversations/..%2Finternal"]);
  });

  it("GIF uçları arama metnini ve dili sorguya yazar", async () => {
    await messagingApi.gifTrending({ limit: 24, offset: 24 });
    await messagingApi.gifSearch({ q: "gg wp", limit: 24, offset: 0, lang: "en" });
    expect(calls()).toEqual([
      "GET /api/v1/messaging/gifs/trending?limit=24&offset=24",
      "GET /api/v1/messaging/gifs/search?q=gg+wp&limit=24&offset=0&lang=en"
    ]);
  });

  it("görsel yükleme ham gövdeyle ek ucuna gider", () => {
    const file = new Blob(["x"], { type: "image/png" });
    const progress = vi.fn();
    messagingApi.upload(C, file, progress);
    expect(uploadRequest).toHaveBeenCalledWith(`/api/v1/messaging/conversations/${C}/attachments`, file, progress);
  });
});
