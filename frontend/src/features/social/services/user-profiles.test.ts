import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";

const socialRequest = vi.fn();
vi.mock("./social-http", () => ({ socialRequest: (...args: unknown[]) => socialRequest(...args) }));

const { fetchProfileCards, safeAvatarPath } = await import("./user-profiles");

const KEY = "0123456789abcdef0123456789abcdef";

beforeEach(() => {
  socialRequest.mockReset();
});

describe("fetchProfileCards", () => {
  it("boş listede istek atmaz", async () => {
    expect((await fetchProfileCards([])).size).toBe(0);
    expect(socialRequest).not.toHaveBeenCalled();
  });

  it("yinelenenleri tekler, 50'lik parçalara böler ve kartları kimliğe göre eşler", async () => {
    const ids = Array.from({ length: 60 }, (_, index) => `u${index}`);
    socialRequest.mockImplementation(async (_path: string, options: { body: { userIds: string[] } }) => ({
      items: options.body.userIds.map((userId) => ({
        userId,
        username: `n-${userId}`,
        displayName: null,
        avatar: { small: `/api/v1/user/media/${KEY}-s.jpg`, large: `/api/v1/user/media/${KEY}.jpg` }
      }))
    }));
    const cards = await fetchProfileCards([...ids, "u0", "u1"]);
    expect(socialRequest).toHaveBeenCalledTimes(2);
    expect(socialRequest.mock.calls[0][0]).toBe("/api/v1/user/profiles/batch");
    expect(socialRequest.mock.calls[0][1].body.userIds).toHaveLength(50);
    expect(socialRequest.mock.calls[1][1].body.userIds).toHaveLength(10);
    expect(cards.get("u59")).toEqual({
      userId: "u59",
      username: "n-u59",
      displayName: null,
      avatarPath: `/api/v1/user/media/${KEY}-s.jpg`
    });
  });

  it("user-service hatasında boş döner (liste düşmez); iptali ise iletir", async () => {
    socialRequest.mockRejectedValueOnce(new ApiHttpError(503));
    expect((await fetchProfileCards(["a"])).size).toBe(0);

    const controller = new AbortController();
    controller.abort();
    socialRequest.mockRejectedValueOnce(new DOMException("Aborted", "AbortError"));
    await expect(fetchProfileCards(["a"], controller.signal)).rejects.toThrow("Aborted");
  });
});

describe("safeAvatarPath", () => {
  it("yalnız user-service medya yolunu kabul eder", () => {
    expect(safeAvatarPath(`/api/v1/user/media/${KEY}-s.png`)).toBe(`/api/v1/user/media/${KEY}-s.png`);
    expect(safeAvatarPath("https://evil.test/a.png")).toBeNull();
    expect(safeAvatarPath(`/api/v1/user/media/${KEY}.svg`)).toBeNull();
    expect(safeAvatarPath(`/api/v1/user/media/../${KEY}.jpg`)).toBeNull();
    expect(safeAvatarPath(null)).toBeNull();
  });
});
