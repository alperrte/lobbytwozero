import { describe, expect, it } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import en from "@/shared/lib/i18n/messages/en.json";
import { describeReviewError, isNotFound, retryAfterOf } from "./review-errors";

describe("describeReviewError", () => {
  it("HTTP durumunu bağlama göre ayırır", () => {
    expect(describeReviewError(new ApiHttpError(401)).needsLogin).toBe(true);
    expect(describeReviewError(new ApiHttpError(409), "create").key).toBe("409create");
    expect(describeReviewError(new ApiHttpError(409), "create").conflict).toBe(false);
    const version = describeReviewError(new ApiHttpError(409), "update");
    expect(version).toMatchObject({ key: "409version", conflict: true });
    expect(describeReviewError(new ApiHttpError(409), "delete").conflict).toBe(true);
    expect(describeReviewError(new ApiHttpError(404), "read").key).toBe("404");
    expect(describeReviewError(new ApiHttpError(404), "rating").key).toBe("404game");
    expect(describeReviewError(new ApiHttpError(404), "update").key).toBe("404review");
    expect(describeReviewError(new ApiHttpError(403), "like").key).toBe("403like");
    expect(describeReviewError(new ApiHttpError(403), "delete").key).toBe("403owner");
    expect(describeReviewError(new ApiHttpError(403), "read").key).toBe("403");
    expect(describeReviewError(new ApiHttpError(400), "create").key).toBe("invalidBody");
    expect(describeReviewError(new ApiHttpError(400), "rating").key).toBe("badRequest");
    expect(describeReviewError(new ApiHttpError(413), "create").key).toBe("413");
    expect(describeReviewError(new ApiHttpError(503), "rating").key).toBe("503");
    expect(describeReviewError(new ApiHttpError(500)).key).toBe("500");
    expect(describeReviewError(new ApiHttpError(502)).key).toBe("500");
  });

  it("rapor bağlamında durumları ayırır", () => {
    expect(describeReviewError(new ApiHttpError(409), "report")).toMatchObject({ key: "409report", conflict: false });
    expect(describeReviewError(new ApiHttpError(403), "report").key).toBe("403report");
    expect(describeReviewError(new ApiHttpError(404), "report").key).toBe("404review");
    expect(describeReviewError(new ApiHttpError(400), "report").key).toBe("invalidReport");
    expect(describeReviewError(new ApiHttpError(429, { retryAfterSeconds: 9 }), "report")).toMatchObject({ key: "429wait", retryAfterSeconds: 9 });
  });

  it("429'da Retry-After saniyesini taşır; yoksa genel mesaj", () => {
    expect(describeReviewError(new ApiHttpError(429, { retryAfterSeconds: 42 }), "like")).toMatchObject({ key: "429wait", retryAfterSeconds: 42 });
    expect(describeReviewError(new ApiHttpError(429), "like").key).toBe("429");
    expect(retryAfterOf(new ApiHttpError(429, { retryAfterSeconds: 7 }))).toBe(7);
    expect(retryAfterOf(new ApiHttpError(500, { retryAfterSeconds: 7 }))).toBeUndefined();
    expect(retryAfterOf(new Error("x"))).toBeUndefined();
  });

  it("REVIEW_MODERATED kodunu yetki hatasından ayırır", () => {
    for (const context of ["update", "delete", "like", "read"] as const) {
      expect(describeReviewError(new ApiHttpError(403, { code: "REVIEW_MODERATED" }), context)).toMatchObject({ key: "moderated", needsLogin: false, conflict: false });
    }
  });

  it("CSRF reddini durumdan bağımsız ayırır", () => {
    expect(describeReviewError(new ApiHttpError(403, { code: "CSRF_TOKEN_INVALID" }), "like").key).toBe("csrf");
  });

  it("ağ/zaman aşımı hatasını sonucu bilinmeyen olarak işaretler", () => {
    expect(describeReviewError(new TypeError("Failed to fetch"))).toMatchObject({ key: "network", unknownOutcome: true });
    expect(describeReviewError(new ApiHttpError(500)).unknownOutcome).toBe(false);
  });

  it("sunucu metnini arayüze taşımaz", () => {
    const error = new ApiHttpError(409, { code: "SQL_ERROR_DETAIL" });
    expect(Object.values(describeReviewError(error, "update")).join(" ")).not.toContain("SQL");
  });

  it("üretilebilecek her hata anahtarı TR ve EN'de vardır", () => {
    const statuses = [400, 401, 403, 404, 409, 413, 429, 500, 503];
    const contexts = ["read", "rating", "recommendation", "create", "update", "delete", "like", "report"] as const;
    const keys = new Set<string>(["network", "csrf", "moderated"]);
    for (const status of statuses) {
      for (const context of contexts) {
        keys.add(describeReviewError(new ApiHttpError(status, { retryAfterSeconds: status === 429 ? 5 : undefined }), context).key);
      }
    }
    keys.add(describeReviewError(new ApiHttpError(429), "like").key);
    for (const key of keys) {
      expect((tr.Review.errors as Record<string, string>)[key], `tr ${key}`).toBeTruthy();
      expect((en.Review.errors as Record<string, string>)[key], `en ${key}`).toBeTruthy();
    }
  });
});

describe("isNotFound", () => {
  it("yalnız 404'ü tanır", () => {
    expect(isNotFound(new ApiHttpError(404))).toBe(true);
    expect(isNotFound(new ApiHttpError(403))).toBe(false);
    expect(isNotFound(new Error("404"))).toBe(false);
  });
});
