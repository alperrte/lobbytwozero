import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import { socialRequest } from "./social-http";

const fetchMock = vi.fn<typeof fetch>();

function json(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" }
  });
}

function setCsrfCookie(value: string | null) {
  document.cookie = value === null ? "XSRF-TOKEN=; max-age=0; path=/" : `XSRF-TOKEN=${value}; path=/`;
}

function call(index: number) {
  const [url, init] = fetchMock.mock.calls[index];
  return { url: String(url), init: init ?? {}, headers: new Headers(init?.headers) };
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  setCsrfCookie(null);
});

afterEach(() => {
  vi.unstubAllGlobals();
  setCsrfCookie(null);
});

describe("socialRequest", () => {
  it("okumalar CSRF istemez ve çerezle gider", async () => {
    fetchMock.mockResolvedValueOnce(json(200, { items: [], nextCursor: null }));
    await socialRequest("/api/v1/social/friends?limit=12");
    const { url, init, headers } = call(0);
    expect(url).toMatch(/\/api\/v1\/social\/friends\?limit=12$/);
    expect(init.method).toBe("GET");
    expect(init.credentials).toBe("include");
    expect(headers.has("X-XSRF-TOKEN")).toBe(false);
  });

  it("yazmalarda çerezdeki CSRF değerini başlıkta ve gövdeyi JSON olarak gönderir", async () => {
    setCsrfCookie("tok-1");
    fetchMock.mockResolvedValueOnce(json(201, { blockedUserId: "u", createdAt: "2026-11-07T00:00:00Z" }));
    await socialRequest("/api/v1/social/blocks", { method: "POST", body: { blockedUserId: "u" } });
    const { init, headers } = call(0);
    expect(headers.get("X-XSRF-TOKEN")).toBe("tok-1");
    expect(headers.get("Content-Type")).toBe("application/json");
    expect(init.body).toBe(JSON.stringify({ blockedUserId: "u" }));
  });

  it("çerez yoksa önce CSRF ucundan alır", async () => {
    fetchMock.mockImplementationOnce(async () => {
      setCsrfCookie("fresh");
      return new Response(null, { status: 204 });
    });
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await socialRequest("/api/v1/social/invite-code", { method: "DELETE" });
    expect(call(0).url).toMatch(/\/api\/v1\/auth\/csrf$/);
    expect(call(1).headers.get("X-XSRF-TOKEN")).toBe("fresh");
  });

  it("Gateway CSRF reddinde çerezi yenileyip isteği bir kez tekrarlar", async () => {
    setCsrfCookie("stale");
    fetchMock.mockResolvedValueOnce(json(403, { code: "CSRF_TOKEN_INVALID" }));
    fetchMock.mockImplementationOnce(async () => {
      setCsrfCookie("renewed");
      return new Response(null, { status: 204 });
    });
    fetchMock.mockResolvedValueOnce(json(200, { id: "r1" }));

    await expect(
      socialRequest("/api/v1/social/friend-requests/r1/accept", { method: "POST" })
    ).resolves.toEqual({ id: "r1" });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(call(2).headers.get("X-XSRF-TOKEN")).toBe("renewed");
  });

  it("başka hataları (servise ulaşmış POST) asla tekrarlamaz", async () => {
    setCsrfCookie("tok");
    fetchMock.mockResolvedValueOnce(json(409, { code: "ALREADY_FRIENDS" }));
    const error = await socialRequest("/api/v1/social/friend-requests/by-code", {
      method: "POST",
      body: { code: "ABCDE12345" }
    }).catch((reason: unknown) => reason);
    expect(error).toBeInstanceOf(ApiHttpError);
    expect((error as ApiHttpError).code).toBe("ALREADY_FRIENDS");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("CSRF ikinci kez reddedilirse hatayı iletir", async () => {
    setCsrfCookie("tok");
    fetchMock.mockResolvedValueOnce(json(403, { code: "CSRF_TOKEN_INVALID" }));
    fetchMock.mockImplementationOnce(async () => {
      setCsrfCookie("tok2");
      return new Response(null, { status: 204 });
    });
    fetchMock.mockResolvedValueOnce(json(403, { code: "CSRF_TOKEN_INVALID" }));
    await expect(socialRequest("/api/v1/social/blocks/u", { method: "DELETE" })).rejects.toMatchObject({
      code: "CSRF_TOKEN_INVALID"
    });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });
});
