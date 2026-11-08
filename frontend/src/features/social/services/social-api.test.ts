import { beforeEach, describe, expect, it, vi } from "vitest";

const socialRequest = vi.fn();
vi.mock("./social-http", () => ({ socialRequest: (...args: unknown[]) => socialRequest(...args) }));

const { socialApi } = await import("./social-api");

beforeEach(() => {
  socialRequest.mockReset();
  socialRequest.mockResolvedValue(undefined);
});

describe("socialApi uç eşlemesi (docs/api.md → SOCIAL)", () => {
  it("liste uçları limit ve imleci sorguya yazar", async () => {
    await socialApi.listFriends({ limit: 12, cursor: "c+/=" });
    await socialApi.listIncomingRequests();
    await socialApi.listOutgoingRequests({ limit: 3 });
    await socialApi.listBlocks({ limit: 20 });
    expect(socialRequest.mock.calls.map(([path]) => path)).toEqual([
      "/api/v1/social/friends?limit=12&cursor=c%2B%2F%3D",
      "/api/v1/social/friend-requests/incoming",
      "/api/v1/social/friend-requests/outgoing?limit=3",
      "/api/v1/social/blocks?limit=20"
    ]);
  });

  it("istek işlemleri doğru yola POST atar ve kimliği kaçırır", async () => {
    await socialApi.acceptRequest("r1");
    await socialApi.rejectRequest("r2");
    await socialApi.cancelRequest("../x");
    expect(socialRequest.mock.calls).toEqual([
      ["/api/v1/social/friend-requests/r1/accept", { method: "POST" }],
      ["/api/v1/social/friend-requests/r2/reject", { method: "POST" }],
      ["/api/v1/social/friend-requests/..%2Fx/cancel", { method: "POST" }]
    ]);
  });

  it("davet koduyla istek kodu normalleştirip gövdede gönderir", async () => {
    await socialApi.sendRequestByCode(" abcde-12345 ");
    expect(socialRequest).toHaveBeenCalledWith("/api/v1/social/friend-requests/by-code", {
      method: "POST",
      body: { code: "ABCDE12345" }
    });
  });

  it("arkadaşlık, engel ve davet kodu uçları", async () => {
    await socialApi.removeFriend("f1");
    await socialApi.blockUser("u1");
    await socialApi.unblockUser("u1");
    await socialApi.getInviteCode();
    await socialApi.createInviteCode();
    await socialApi.revokeInviteCode();
    expect(socialRequest.mock.calls.map(([path, options]) => `${options?.method ?? "GET"} ${path}`)).toEqual([
      "DELETE /api/v1/social/friends/f1",
      "POST /api/v1/social/blocks",
      "DELETE /api/v1/social/blocks/u1",
      "GET /api/v1/social/invite-code",
      "POST /api/v1/social/invite-code",
      "DELETE /api/v1/social/invite-code"
    ]);
    expect(socialRequest.mock.calls[1][1]).toEqual({ method: "POST", body: { blockedUserId: "u1" } });
  });
});
