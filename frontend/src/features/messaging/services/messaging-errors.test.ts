import { describe, expect, it } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { isAbort, messagingErrorKey } from "./messaging-errors";

describe("messagingErrorKey", () => {
  it.each([
    ["CONVERSATION_NOT_FOUND", 404, "conversationNotFound"],
    ["MESSAGING_NOT_ALLOWED", 403, "notAllowed"],
    ["BLOCK_CHECK_UNAVAILABLE", 503, "blockCheckUnavailable"],
    ["DELETE_WINDOW_EXPIRED", 422, "deleteWindowExpired"],
    ["TOO_MANY_MESSAGES", 429, "tooManyMessages"],
    ["GIF_PROVIDER_UNAVAILABLE", 503, "gifUnavailable"],
    ["ATTACHMENT_NOT_AVAILABLE", 422, "attachmentNotAvailable"],
    ["SERVICE_BUSY", 503, "busy"]
  ])("sunucu kodu %s → %s", (code, status, key) => {
    expect(messagingErrorKey(new ApiHttpError(status, { code }))).toBe(key);
  });

  it("bilinmeyen kodda HTTP durumuna düşer", () => {
    expect(messagingErrorKey(new ApiHttpError(401))).toBe("unauthenticated");
    expect(messagingErrorKey(new ApiHttpError(413))).toBe("payloadTooLarge");
    expect(messagingErrorKey(new ApiHttpError(429, { code: "YENI_KOD" }))).toBe("rateLimited");
    expect(messagingErrorKey(new ApiHttpError(502))).toBe("unavailable");
    expect(messagingErrorKey(new ApiHttpError(500))).toBe("generic");
  });

  it("ağ, zaman aşımı ve iptal ayrılır", () => {
    expect(messagingErrorKey(new TypeError("fetch failed"))).toBe("network");
    expect(messagingErrorKey(new DOMException("t", "TimeoutError"))).toBe("timeout");
    expect(messagingErrorKey(new Error("x"))).toBe("generic");
    expect(isAbort(new DOMException("a", "AbortError"))).toBe(true);
    expect(isAbort(new Error("a"))).toBe(false);
  });

  it("her hata anahtarının iki dilde metni vardır", () => {
    const codes = [
      "CONVERSATION_NOT_FOUND", "MESSAGE_NOT_FOUND", "MESSAGING_NOT_ALLOWED", "BLOCK_CHECK_UNAVAILABLE", "MESSAGE_DELETED",
      "DELETE_WINDOW_EXPIRED", "NOT_ALLOWED_TO_DELETE", "TOO_MANY_MESSAGES", "TOO_MANY_UPLOADS", "TOO_MANY_PENDING_ATTACHMENTS",
      "TOO_MANY_REACTIONS", "TOO_MANY_GIF_SEARCHES", "GIF_PROVIDER_UNAVAILABLE", "GIF_NOT_AVAILABLE", "ATTACHMENT_NOT_AVAILABLE",
      "INVALID_IMAGE", "IMAGE_TOO_LARGE", "IMAGE_DIMENSIONS_EXCEEDED", "UNSUPPORTED_MEDIA_TYPE", "MEDIA_BUSY", "PAYLOAD_TOO_LARGE",
      "INVALID_REACTION", "INSUFFICIENT_GROUP_ROLE", "ALREADY_MEMBER", "GROUP_MEMBER_LIMIT_REACHED", "MEMBER_NOT_FOUND",
      "CANNOT_MESSAGE_SELF", "VALIDATION_FAILED", "SERVICE_BUSY", "CSRF_TOKEN_INVALID"
    ];
    const keys = new Set([
      ...codes.map((code) => messagingErrorKey(new ApiHttpError(400, { code }))),
      ...[401, 403, 404, 409, 413, 429, 503, 500].map((status) => messagingErrorKey(new ApiHttpError(status))),
      "timeout",
      "network"
    ]);
    for (const key of keys) {
      expect(tr.Messaging.errors[key as keyof typeof tr.Messaging.errors]).toBeTruthy();
      expect(en.Messaging.errors[key as keyof typeof en.Messaging.errors]).toBeTruthy();
    }
  });
});
