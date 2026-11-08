import { beforeEach, describe, expect, it } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import { MOCK_MY_INVITE_CODE, mockIds } from "./social-mock-data";
import { resetSocialMock, socialMockApi as api } from "./social-mock-store";

async function errorCode(promise: Promise<unknown>): Promise<string | undefined> {
  try {
    await promise;
  } catch (error) {
    expect(error).toBeInstanceOf(ApiHttpError);
    return (error as ApiHttpError).code;
  }
  throw new Error("hata bekleniyordu");
}

async function counts() {
  const all = { limit: 50 };
  const [friends, incoming, outgoing, blocked] = await Promise.all([
    api.listFriends(all),
    api.listIncomingRequests(all),
    api.listOutgoingRequests(all),
    api.listBlocks(all)
  ]);
  return {
    friends: friends.items.length,
    incoming: incoming.items.length,
    outgoing: outgoing.items.length,
    blocked: blocked.items.length
  };
}

beforeEach(() => {
  resetSocialMock({ latencyMs: 0 });
});

describe("sahte social-service: sayfalama", () => {
  it("imleçle sayfalar ve son sayfada nextCursor null döner", async () => {
    const first = await api.listFriends({ limit: 20 });
    expect(first.items).toHaveLength(20);
    expect(first.nextCursor).not.toBeNull();
    const second = await api.listFriends({ limit: 20, cursor: first.nextCursor });
    expect(second.items).toHaveLength(4);
    expect(second.nextCursor).toBeNull();
  });

  it("sunucuyla aynı sayfa boyutu ve imleç kurallarını uygular", async () => {
    expect(await errorCode(api.listFriends({ limit: 51 }))).toBe("INVALID_PAGE_SIZE");
    expect(await errorCode(api.listFriends({ limit: 0 }))).toBe("INVALID_PAGE_SIZE");
    expect(await errorCode(api.listFriends({ cursor: "bozuk" }))).toBe("INVALID_CURSOR");
  });
});

describe("sahte social-service: istekler", () => {
  it("kabul edilen istek arkadaş listesine eklenir, gelenlerden düşer", async () => {
    const [request] = (await api.listIncomingRequests()).items;
    const answered = await api.acceptRequest(request.id);
    expect(answered.status).toBe("ACCEPTED");
    expect((await api.listIncomingRequests()).items.map((r) => r.id)).not.toContain(request.id);
    expect((await api.listFriends({ limit: 50 })).items.map((f) => f.friendId)).toContain(request.senderId);
  });

  it("yanıtlanmış isteğe ikinci yanıt 409, başkasının isteği 404 olur", async () => {
    const [request] = (await api.listIncomingRequests()).items;
    await api.rejectRequest(request.id);
    expect(await errorCode(api.acceptRequest(request.id))).toBe("FRIEND_REQUEST_NOT_PENDING");
    const [outgoing] = (await api.listOutgoingRequests()).items;
    expect(await errorCode(api.acceptRequest(outgoing.id))).toBe("FRIEND_REQUEST_NOT_FOUND");
  });

  it("davet koduyla istek: kendi kodu, arkadaş, engelli ve bilinmeyen kod ayrı hatalardır", async () => {
    expect(await errorCode(api.sendRequestByCode(MOCK_MY_INVITE_CODE))).toBe("CANNOT_REQUEST_SELF");
    expect(await errorCode(api.sendRequestByCode("DENZ1PKYS2"))).toBe("ALREADY_FRIENDS");
    expect(await errorCode(api.sendRequestByCode("GHST0RNNR1"))).toBe("USER_BLOCKED");
    expect(await errorCode(api.sendRequestByCode("ZZZZZZZZZZ"))).toBe("INVITE_CODE_NOT_FOUND");
    expect(await errorCode(api.sendRequestByCode("kısa"))).toBe("VALIDATION_FAILED");
  });

  it("geçerli kod gönderilen isteği oluşturur; ikinci kez aynı kişiye 409 döner", async () => {
    const request = await api.sendRequestByCode("sarp7-q2m4k");
    expect(request).toMatchObject({ status: "PENDING", receiverId: mockIds.strangers[0] });
    expect((await counts()).outgoing).toBe(3);
    expect(await errorCode(api.sendRequestByCode("SARP7Q2M4K"))).toBe("FRIEND_REQUEST_ALREADY_PENDING");
  });
});

describe("sahte social-service: engelleme", () => {
  it("engel arkadaşlığı ve bekleyen istekleri bitirir; tekrar engel aynı kaydı döner", async () => {
    const friendId = mockIds.friends[0];
    const incomingSender = mockIds.incoming[0];
    await api.blockUser(friendId);
    await api.blockUser(incomingSender);
    const again = await api.blockUser(friendId);
    expect(again.blockedUserId).toBe(friendId);

    expect(await counts()).toEqual({ friends: 23, incoming: 2, outgoing: 2, blocked: 5 });
  });

  it("engel kaldırma listeden düşürür; olmayan engel 404, kendini engelleme 400 olur", async () => {
    const blocked = mockIds.blocked[0];
    await api.unblockUser(blocked);
    expect((await api.listBlocks()).items.map((b) => b.blockedUserId)).not.toContain(blocked);
    expect(await errorCode(api.unblockUser(blocked))).toBe("BLOCK_NOT_FOUND");
    expect(await errorCode(api.blockUser("00000000-0000-4000-8000-000000000001"))).toBe("CANNOT_BLOCK_SELF");
  });
});

describe("sahte social-service: davet kodu", () => {
  it("yenilenen kod eskisini geçersiz kılar", async () => {
    await api.revokeInviteCode();
    expect(await errorCode(api.getInviteCode())).toBe("INVITE_CODE_NOT_FOUND");
    const created = await api.createInviteCode();
    expect(created.code).toMatch(/^[0-9A-HJKMNP-TV-Z]{10}$/);
    expect(created.code).not.toBe(MOCK_MY_INVITE_CODE);
    expect((await api.createInviteCode()).code).toBe(created.code);
  });
});
