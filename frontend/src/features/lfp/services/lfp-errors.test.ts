import { describe, expect, it } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { fieldErrorsOf, lfpErrorKey } from "./lfp-errors";

describe("lfpErrorKey", () => {
  it.each([
    ["LISTING_NOT_FOUND", 404, "listingNotFound"],
    ["OPEN_LISTING_LIMIT", 409, "openLimit"],
    ["LISTING_NOT_OPEN", 409, "notOpen"],
    ["GAME_CATALOG_UNAVAILABLE", 503, "catalogUnavailable"],
    ["BLOCK_CHECK_UNAVAILABLE", 503, "blockCheckUnavailable"],
    ["CANNOT_CONTACT_SELF", 400, "cannotContactSelf"],
    ["INVALID_ROLES", 400, "invalidField"]
  ])("%s → %s", (code, status, key) => {
    expect(lfpErrorKey(new ApiHttpError(status, { code }))).toBe(key);
  });

  it("bilinmeyen kodda HTTP durumuna, ağ ve zaman aşımında kendi anahtarına düşer", () => {
    expect(lfpErrorKey(new ApiHttpError(401))).toBe("unauthenticated");
    expect(lfpErrorKey(new ApiHttpError(429))).toBe("rateLimited");
    expect(lfpErrorKey(new ApiHttpError(500))).toBe("generic");
    expect(lfpErrorKey(new TypeError("x"))).toBe("network");
    expect(lfpErrorKey(new DOMException("t", "TimeoutError"))).toBe("timeout");
  });

  it("her anahtarın iki dilde metni vardır", () => {
    const keys = Object.keys(tr.Lfp.errors);
    expect(Object.keys(en.Lfp.errors).sort()).toEqual([...keys].sort());
    for (const status of [400, 401, 403, 404, 409, 429, 503, 500]) {
      expect(keys).toContain(lfpErrorKey(new ApiHttpError(status)));
    }
  });
});

describe("fieldErrorsOf", () => {
  it("alan hatalarını ve alana özgü kodları form alanlarına çevirir", () => {
    const error = new ApiHttpError(400, { code: "INVALID_PLAY_TIME", fieldErrors: [{ field: "roles[0]", code: "Size" }, { field: "description", code: "NotBlank" }] });
    expect(fieldErrorsOf(error).sort()).toEqual(["description", "playTimeStart", "roles"]);
    expect(fieldErrorsOf(new Error("x"))).toEqual([]);
  });
});
