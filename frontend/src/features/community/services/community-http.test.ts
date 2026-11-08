import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import { errorKeyOf, isAbort, statusOf } from "./community-errors";
import { communityGet, communitySend, communityUpload } from "./community-http";
import { safeAvatarUrl } from "./community-api";

const apiRequest = vi.fn();
vi.mock("@/shared/lib/api/client", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/shared/lib/api/client")>();
  return { ...original, apiRequest: (...args: unknown[]) => apiRequest(...args) };
});

function setCookie(value: string | null) {
  document.cookie = `XSRF-TOKEN=${value ?? ""}; path=/; max-age=${value ? 60 : 0}`;
}

beforeEach(() => {
  apiRequest.mockReset();
  setCookie(null);
});
afterEach(() => setCookie(null));

describe("Community HTTP geçidi", () => {
  it("okuma CSRF istemez; çerezli ortak istemciden geçer", async () => {
    apiRequest.mockResolvedValueOnce({ items: [] });
    await communityGet("/api/v1/community/posts?limit=20");
    expect(apiRequest).toHaveBeenCalledTimes(1);
    expect(apiRequest.mock.calls[0]?.[0]).toBe("/api/v1/community/posts?limit=20");
    expect(apiRequest.mock.calls[0]?.[1].headers["X-XSRF-TOKEN"]).toBeUndefined();
  });

  it("yazmadan önce CSRF çerezini hazırlar ve başlıkta gönderir", async () => {
    apiRequest.mockImplementation(async (path: string) => {
      if (path === "/api/v1/auth/csrf") {
        setCookie("tok-1");
        return undefined;
      }
      return { liked: true };
    });
    await communitySend("/api/v1/community/posts/p1/like", "PUT");
    const write = apiRequest.mock.calls.at(-1)!;
    expect(write[0]).toBe("/api/v1/community/posts/p1/like");
    expect(write[1].method).toBe("PUT");
    expect(write[1].headers["X-XSRF-TOKEN"]).toBe("tok-1");
    expect(write[1].body).toBeUndefined();
  });

  it("JSON gövdede Content-Type, yüklemede ise yalnız FormData gönderir (tarayıcı sınırı kurar)", async () => {
    setCookie("tok-2");
    apiRequest.mockResolvedValue({});
    await communitySend("/api/v1/community/posts", "POST", { body: "x" });
    expect(apiRequest.mock.calls[0]?.[1].headers["Content-Type"]).toBe("application/json");
    expect(apiRequest.mock.calls[0]?.[1].body).toBe('{"body":"x"}');

    await communityUpload("/api/v1/community/posts/p1/images", new File(["a"], "a.png", { type: "image/png" }));
    const upload = apiRequest.mock.calls[1]![1];
    expect(upload.body).toBeInstanceOf(FormData);
    expect(upload.headers["Content-Type"]).toBeUndefined();
    expect(upload.headers["X-XSRF-TOKEN"]).toBe("tok-2");
  });

  it("CSRF reddinde yenileyip yalnız bir kez tekrarlar; başka hatada tekrarlamaz", async () => {
    setCookie("stale");
    let writes = 0;
    apiRequest.mockImplementation(async (path: string) => {
      if (path === "/api/v1/auth/csrf") {
        setCookie("fresh");
        return undefined;
      }
      writes += 1;
      if (writes === 1) throw new ApiHttpError(403, { code: "CSRF_TOKEN_INVALID" });
      return { ok: true };
    });
    await expect(communitySend("/api/v1/community/posts", "POST", {})).resolves.toEqual({ ok: true });
    expect(writes).toBe(2);

    apiRequest.mockReset();
    apiRequest.mockRejectedValue(new ApiHttpError(409));
    await expect(communitySend("/api/v1/community/posts", "POST", {})).rejects.toMatchObject({ status: 409 });
    expect(apiRequest).toHaveBeenCalledTimes(1);
  });
});

describe("Community hata eşleme", () => {
  it.each([
    [400, "validation"],
    [401, "unauthorized"],
    [403, "forbidden"],
    [404, "notFound"],
    [409, "conflict"],
    [413, "tooLarge"],
    [415, "unsupportedMedia"],
    [422, "unprocessable"],
    [429, "rateLimited"],
    [503, "unavailable"],
    [500, "server"]
  ])("%i → %s", (status, key) => {
    expect(errorKeyOf(new ApiHttpError(status))).toBe(key);
    expect(statusOf(new ApiHttpError(status))).toBe(status);
  });

  it("ağ hatası ve iptal ayrıdır", () => {
    expect(errorKeyOf(new TypeError("fetch failed"))).toBe("network");
    expect(isAbort(new DOMException("x", "AbortError"))).toBe(true);
    expect(isAbort(new Error("x"))).toBe(false);
  });
});

describe("Avatar adresi güvenliği", () => {
  it("yalnız Gateway medya yolu ya da https kabul edilir", () => {
    const hash = "a".repeat(32);
    expect(safeAvatarUrl(`/api/v1/user/media/${hash}.png`)).toMatch(new RegExp(`/api/v1/user/media/${hash}\\.png$`));
    expect(safeAvatarUrl("https://cdn.example.test/a.png")).toBe("https://cdn.example.test/a.png");
    expect(safeAvatarUrl("javascript:alert(1)")).toBeNull();
    expect(safeAvatarUrl("http://evil.example.test/a.png")).toBeNull();
    expect(safeAvatarUrl("/api/v1/user/media/../../secret.png")).toBeNull();
    expect(safeAvatarUrl(null)).toBeNull();
  });
});
