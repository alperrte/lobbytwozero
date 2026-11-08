import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockIds, mockProfileCards } from "./social-mock-data";
import { resetSocialMock, socialMockApi } from "./social-mock-store";

vi.mock("./social-api", async () => ({ socialApi: (await import("./social-mock-store")).socialMockApi }));
const fetchProfileCards = vi.fn(async (ids: readonly string[]) => mockProfileCards(ids));
vi.mock("./user-profiles", () => ({ fetchProfileCards: (ids: readonly string[]) => fetchProfileCards(ids) }));

const service = await import("./social-service");

beforeEach(() => {
  resetSocialMock({ latencyMs: 0 });
  fetchProfileCards.mockClear();
});

describe("social-service cephesi", () => {
  it("kayıtları tek toplu profil isteğiyle ad/kullanıcı adıyla zenginleştirir", async () => {
    const page = await service.listFriends({ limit: 5 });
    expect(fetchProfileCards).toHaveBeenCalledTimes(1);
    expect(fetchProfileCards.mock.calls[0][0]).toHaveLength(5);
    expect(page.items[0].profile).toMatchObject({ displayName: expect.any(String), username: expect.any(String) });
    expect(page.items[0].friend.friendId).toBe(page.items[0].profile.userId);
  });

  it("engellenenler için user-service'e sorulmaz (kart zaten verilmez); kişiler adsız ve deterministik döner", async () => {
    const first = await service.listBlocks();
    const second = await service.listBlocks();
    expect(fetchProfileCards).not.toHaveBeenCalled();
    expect(first.items.map((item) => item.profile.displayName)).toEqual([null, null, null]);
    expect(first.items.map((item) => item.presence.status)).toEqual(second.items.map((item) => item.presence.status));
  });

  it("sayaçlar listelerden türetilir; 50'yi aşan liste 'more' ile işaretlenir", async () => {
    expect(await service.getCounts()).toEqual({
      friends: { count: 24, more: false },
      incoming: { count: 3, more: false },
      outgoing: { count: 2, more: false },
      blocked: { count: 3, more: false }
    });

    const listFriends = vi.spyOn(socialMockApi, "listFriends").mockResolvedValueOnce({
      items: Array.from({ length: 50 }, (_, index) => ({ friendId: `f${index}`, since: "2026-01-01T00:00:00Z" })),
      nextCursor: "next"
    });
    expect((await service.getCounts()).friends).toEqual({ count: 50, more: true });
    expect(listFriends).toHaveBeenCalledWith({ limit: 50 }, undefined);
    listFriends.mockRestore();
  });

  it("davet kodunu yenilemek eskisini iptal edip yenisini üretir", async () => {
    const before = await service.getInviteCode();
    const renewed = await service.socialActions.regenerateInviteCode();
    expect(renewed.code).not.toBe(before.code);
  });

  it("gelen istekler göndereni, gidenler alıcıyı kişi olarak çözer", async () => {
    const incoming = await service.listIncomingRequests();
    const outgoing = await service.listOutgoingRequests();
    expect(incoming.items.map((item) => item.profile.userId)).toEqual(mockIds.incoming);
    expect(outgoing.items.map((item) => item.profile.userId).sort()).toEqual([...mockIds.outgoing].sort());
  });
});
