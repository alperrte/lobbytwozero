import { describe, expect, it } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import { socialErrorKey } from "./social-errors";

describe("socialErrorKey", () => {
  it("social-service hata kodunu kendi mesajına eşler", () => {
    expect(socialErrorKey(new ApiHttpError(409, { code: "ALREADY_FRIENDS" }))).toBe("alreadyFriends");
    expect(socialErrorKey(new ApiHttpError(404, { code: "INVITE_CODE_NOT_FOUND" }))).toBe("inviteNotFound");
    expect(socialErrorKey(new ApiHttpError(429, { code: "TOO_MANY_PENDING_REQUESTS" }))).toBe("tooManyPending");
  });

  it("kod yoksa HTTP durumunu ayrı ayrı ele alır (tek genel mesaja indirgenmez)", () => {
    expect(socialErrorKey(new ApiHttpError(401))).toBe("unauthenticated");
    expect(socialErrorKey(new ApiHttpError(403))).toBe("forbidden");
    expect(socialErrorKey(new ApiHttpError(404))).toBe("notFound");
    expect(socialErrorKey(new ApiHttpError(409))).toBe("conflict");
    expect(socialErrorKey(new ApiHttpError(429))).toBe("rateLimited");
    expect(socialErrorKey(new ApiHttpError(503))).toBe("unavailable");
    expect(socialErrorKey(new ApiHttpError(500))).toBe("generic");
  });

  it("ağ hatasını ve bilinmeyen hatayı ayırır", () => {
    expect(socialErrorKey(new TypeError("Failed to fetch"))).toBe("network");
    expect(socialErrorKey(new Error("x"))).toBe("generic");
  });
});
