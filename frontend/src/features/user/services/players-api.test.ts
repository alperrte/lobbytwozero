import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";

const userRequest = vi.fn();
vi.mock("./user-http", () => ({ userRequest: (...args: unknown[]) => userRequest(...args) }));

import { getPublicProfile, normalizeSearch, searchPlayers, toPublicProfile } from "./players-api";

const ID = "0b5d6f0e-1c2d-4e3f-8a9b-0c1d2e3f4a5b";

beforeEach(() => userRequest.mockReset());

describe("toPublicProfile", () => {
  it("görünürlük bayrakları yalnız açıkça true ise açıktır", () => {
    const profile = toPublicProfile({ userId: ID, username: "ali", visible: { friendsList: true, library: "true", hardware: 1 } });
    expect(profile.visible).toEqual({ friendsList: true, library: false, hardware: false, performance: false });
    expect(profile.details).toBeNull();
    expect(profile.username).toBe("ali");
  });

  it("geçerli Steam bağlantısını alır, şüpheli adresi yok sayar", () => {
    const good = toPublicProfile({
      details: { steam: { steamId: "76561198000000000", profileUrl: "https://steamcommunity.com/profiles/76561198000000000" } }
    });
    expect(good.details?.steam?.steamId).toBe("76561198000000000");
    const evil = toPublicProfile({
      details: { steam: { steamId: "76561198000000000", profileUrl: "https://evil.example/profiles/76561198000000000" } }
    });
    expect(evil.details?.steam).toBeNull();
    const mismatch = toPublicProfile({
      details: { steam: { steamId: "76561198000000001", profileUrl: "https://steamcommunity.com/profiles/76561198000000000" } }
    });
    expect(mismatch.details?.steam).toBeNull();
  });

  it("bozuk gövdede çökmez", () => {
    expect(toPublicProfile(null).userId).toBe("");
  });
});

describe("getPublicProfile", () => {
  it("UUID ise kimlikle, kullanıcı adı ise adla sorar", async () => {
    userRequest.mockResolvedValue({ userId: ID });
    await getPublicProfile(ID.toUpperCase());
    expect(userRequest.mock.calls[0][0]).toBe(`/api/v1/user/profiles/${ID}`);
    await getPublicProfile("ali_01");
    expect(userRequest.mock.calls[1][0]).toBe("/api/v1/user/profiles/by-username/ali_01");
  });

  it("geçersiz anahtar için istek atmadan 404 verir", async () => {
    await expect(getPublicProfile("../etc")).rejects.toMatchObject({ status: 404 });
    await expect(getPublicProfile("a")).rejects.toBeInstanceOf(ApiHttpError);
    expect(userRequest).not.toHaveBeenCalled();
  });
});

describe("arama", () => {
  it("metni kırpar ve sınırları uygular", () => {
    expect(normalizeSearch("  ali   veli ")).toBe("ali veli");
    expect(normalizeSearch("a")).toBeNull();
    expect(normalizeSearch("x".repeat(33))).toBeNull();
  });

  it("geçersiz sorguda istek atmaz, geçerlide kodlayarak sorar", async () => {
    await expect(searchPlayers("a")).rejects.toMatchObject({ status: 400 });
    expect(userRequest).not.toHaveBeenCalled();
    userRequest.mockResolvedValue([]);
    await expect(searchPlayers("ali veli")).resolves.toEqual([]);
    expect(userRequest.mock.calls[0][0]).toBe("/api/v1/user/search?q=ali%20veli");
  });
});
