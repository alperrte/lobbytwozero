import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";

const userRequest = vi.fn();
vi.mock("./user-http", () => ({ userRequest: (...args: unknown[]) => userRequest(...args) }));

import { searchAdminProfiles, toAdminProfileHits } from "./admin-api";

const ID = "0b5d6f0e-1c2d-4e3f-8a9b-0c1d2e3f4a5b";

beforeEach(() => userRequest.mockReset());

describe("toAdminProfileHits", () => {
  it("alanları okur; kimliği UUID olmayan satırı ve bilinmeyen alanı atar", () => {
    const hits = toAdminProfileHits({
      items: [
        { userId: ID, username: "ada", displayName: " ", accountStatus: "SUSPENDED", memberSince: "2026-01-01T00:00:00Z", bio: "x" },
        { userId: "yok", username: "kötü" },
        null
      ]
    });
    expect(hits).toEqual([
      { userId: ID, username: "ada", displayName: null, accountStatus: "SUSPENDED", memberSince: "2026-01-01T00:00:00Z" }
    ]);
  });

  it("beklenmeyen gövdede boş liste döner", () => {
    expect(toAdminProfileHits(null)).toEqual([]);
    expect(toAdminProfileHits({ items: "x" })).toEqual([]);
  });
});

describe("searchAdminProfiles", () => {
  it("baştaki @ işaretini atar ve sorguyu kodlar", async () => {
    userRequest.mockResolvedValue({ items: [] });
    await searchAdminProfiles("  @Ada Lovelace ");
    expect(userRequest).toHaveBeenCalledWith(
      "/api/v1/user/admin/profiles/search?q=Ada%20Lovelace",
      expect.objectContaining({})
    );
  });

  it("çok kısa ya da çok uzun sorguda istek atmaz", async () => {
    await expect(searchAdminProfiles("@a")).rejects.toBeInstanceOf(ApiHttpError);
    await expect(searchAdminProfiles("a".repeat(33))).rejects.toBeInstanceOf(ApiHttpError);
    await expect(searchAdminProfiles("@")).rejects.toBeInstanceOf(ApiHttpError);
    expect(userRequest).not.toHaveBeenCalled();
  });
});
