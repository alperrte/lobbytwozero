import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthApiError, authRequest } from "./auth-http";

const BASE = "http://localhost:5080";

function reply(status: number, body?: unknown, headers: Record<string, string> = {}): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...headers }
  });
}

function clearCookies() {
  for (const part of document.cookie.split(";")) {
    const name = part.split("=")[0]?.trim();
    if (name) {
      document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
    }
  }
}

/** Çağrıları sırayla yanıtlar; `csrf` yanıtı çerezi yazar (gerçek Gateway gibi). */
function mockFetch(handler: (url: string, init: RequestInit, call: number) => Response | Promise<Response>) {
  let call = 0;
  const fn = vi.fn(async (input: string, init: RequestInit = {}) => {
    const response = await handler(input.replace(BASE, ""), init, call++);
    if (input.endsWith("/csrf")) {
      document.cookie = "XSRF-TOKEN=tok-1; path=/";
    }
    return response;
  });
  vi.stubGlobal("fetch", fn);
  return fn;
}

const paths = (fn: ReturnType<typeof vi.fn>) => fn.mock.calls.map((c) => String(c[0]).replace(BASE, ""));

beforeEach(clearCookies);
afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe("authRequest: CSRF", () => {
  it("çerez yoksa önce /csrf çağırır ve başlığı ekler; GET isteği CSRF istemez", async () => {
    const fn = mockFetch((url) => (url.endsWith("/csrf") ? reply(204) : reply(200, { status: "AUTHENTICATED" })));

    await authRequest("/api/v1/auth/login", { method: "POST", body: { identifier: "a", password: "b" } });
    expect(paths(fn)).toEqual(["/api/v1/auth/csrf", "/api/v1/auth/login"]);
    const init = fn.mock.calls[1]![1] as RequestInit;
    expect(new Headers(init.headers).get("X-XSRF-TOKEN")).toBe("tok-1");
    expect(new Headers(init.headers).get("Content-Type")).toBe("application/json");
    expect(init.credentials).toBe("include");

    fn.mockClear();
    await authRequest("/api/v1/auth/me");
    expect(paths(fn)).toEqual(["/api/v1/auth/me"]);
  });

  it("CSRF_TOKEN_INVALID'de çerezi yeniler ve BİR kez tekrarlar", async () => {
    document.cookie = "XSRF-TOKEN=eski; path=/";
    const fn = mockFetch((url, _init, call) => {
      if (url.endsWith("/csrf")) return reply(204);
      return call === 0 ? reply(403, { code: "CSRF_TOKEN_INVALID" }) : reply(200, { ok: true });
    });

    await expect(authRequest("/api/v1/auth/login", { method: "POST", body: {} })).resolves.toEqual({ ok: true });
    expect(paths(fn)).toEqual(["/api/v1/auth/login", "/api/v1/auth/csrf", "/api/v1/auth/login"]);
  });

  it("CSRF ikinci kez de reddedilirse döngüye girmez", async () => {
    document.cookie = "XSRF-TOKEN=eski; path=/";
    const fn = mockFetch((url) => (url.endsWith("/csrf") ? reply(204) : reply(403, { code: "CSRF_TOKEN_INVALID" })));

    await expect(authRequest("/api/v1/auth/login", { method: "POST", body: {} })).rejects.toMatchObject({
      status: 403,
      code: "CSRF_TOKEN_INVALID"
    });
    expect(fn).toHaveBeenCalledTimes(3);
  });
});

describe("authRequest: 401 → yenile → tek tekrar", () => {
  it("korumalı uçta 401 gelince yeniler ve isteği bir kez tekrarlar", async () => {
    document.cookie = "XSRF-TOKEN=tok; path=/";
    let meCalls = 0;
    const fn = mockFetch((url) => {
      if (url.endsWith("/session/refresh")) return reply(200, { status: "AUTHENTICATED" });
      meCalls += 1;
      return meCalls === 1 ? reply(401, { code: "SESSION_INVALID" }) : reply(200, { accountId: "a1" });
    });

    await expect(authRequest("/api/v1/auth/me")).resolves.toEqual({ accountId: "a1" });
    expect(paths(fn)).toEqual(["/api/v1/auth/me", "/api/v1/auth/session/refresh", "/api/v1/auth/me"]);
  });

  it("eşzamanlı 401'lerde tek yenileme yürütür", async () => {
    document.cookie = "XSRF-TOKEN=tok; path=/";
    const seen = new Map<string, number>();
    const fn = mockFetch(async (url) => {
      if (url.endsWith("/session/refresh")) {
        await new Promise((resolve) => setTimeout(resolve, 20));
        return reply(200, { status: "AUTHENTICATED" });
      }
      const n = (seen.get(url) ?? 0) + 1;
      seen.set(url, n);
      return n === 1 ? reply(401) : reply(200, { url });
    });

    const results = await Promise.all([authRequest("/api/v1/auth/me"), authRequest("/api/v1/auth/devices")]);
    expect(results).toHaveLength(2);
    expect(paths(fn).filter((p) => p.endsWith("/session/refresh"))).toHaveLength(1);
  });

  it("yenileme de 401 verirse özgün 401'i fırlatır ve tekrar denemez", async () => {
    document.cookie = "XSRF-TOKEN=tok; path=/";
    const fn = mockFetch((url) => (url.endsWith("/session/refresh") ? reply(401, { code: "SESSION_INVALID" }) : reply(401)));

    await expect(authRequest("/api/v1/auth/me")).rejects.toMatchObject({ status: 401 });
    expect(paths(fn)).toEqual(["/api/v1/auth/me", "/api/v1/auth/session/refresh"]);
  });

  it("tekrar edilen istek yine 401 verirse ikinci kez yenilemez", async () => {
    document.cookie = "XSRF-TOKEN=tok; path=/";
    const fn = mockFetch((url) => (url.endsWith("/session/refresh") ? reply(200, {}) : reply(401)));

    await expect(authRequest("/api/v1/auth/me")).rejects.toMatchObject({ status: 401 });
    expect(paths(fn)).toEqual(["/api/v1/auth/me", "/api/v1/auth/session/refresh", "/api/v1/auth/me"]);
  });

  it.each([
    "/api/v1/auth/login",
    "/api/v1/auth/register",
    "/api/v1/auth/email-verification/confirm",
    "/api/v1/auth/email-verification/resend",
    "/api/v1/auth/session/logout"
  ] as const)("%s için 401'de yenileme denemez", async (path) => {
    document.cookie = "XSRF-TOKEN=tok; path=/";
    const fn = mockFetch(() => reply(401, { code: "INVALID_CREDENTIALS" }));

    await expect(authRequest(path, { method: "POST", body: {} })).rejects.toMatchObject({
      status: 401,
      code: "INVALID_CREDENTIALS"
    });
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it("REFRESH_CONFLICT'te kısa bekleyip isteği bir kez tekrarlar", async () => {
    vi.useFakeTimers();
    document.cookie = "XSRF-TOKEN=tok; path=/";
    let meCalls = 0;
    mockFetch((url) => {
      if (url.endsWith("/session/refresh")) return reply(409, { code: "REFRESH_CONFLICT" });
      meCalls += 1;
      return meCalls === 1 ? reply(401) : reply(200, { ok: true });
    });

    const pending = authRequest("/api/v1/auth/me");
    await vi.advanceTimersByTimeAsync(300);
    await expect(pending).resolves.toEqual({ ok: true });
  });
});

describe("authRequest: hata gövdesi", () => {
  it("code, errors[] ve Retry-After'ı taşır", async () => {
    document.cookie = "XSRF-TOKEN=tok; path=/";
    mockFetch(() =>
      reply(
        429,
        { code: "TOO_MANY_ATTEMPTS", errors: [{ field: "password", code: "x" }, { bozuk: true }, "metin"] },
        { "Retry-After": "42" }
      )
    );

    const error = await authRequest("/api/v1/auth/login", { method: "POST", body: {} }).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(AuthApiError);
    expect(error).toMatchObject({
      status: 429,
      code: "TOO_MANY_ATTEMPTS",
      retryAfterSeconds: 42,
      fieldErrors: [{ field: "password", code: "x" }]
    });
  });

  it("bilinmeyen kod UNKNOWN olur; bozuk Retry-After yok sayılır; gövde JSON değilse de çalışır", async () => {
    document.cookie = "XSRF-TOKEN=tok; path=/";
    mockFetch(() => new Response("<html>", { status: 500, headers: { "Retry-After": "abc" } }));

    const error = await authRequest("/api/v1/auth/login", { method: "POST", body: {} }).catch((e: unknown) => e);
    expect(error).toMatchObject({ status: 500, code: "UNKNOWN", retryAfterSeconds: undefined });
  });

  it("parola hata mesajına ya da alanlarına sızmaz", async () => {
    document.cookie = "XSRF-TOKEN=tok; path=/";
    mockFetch(() => reply(401, { code: "INVALID_CREDENTIALS", detail: "parola=Gizli-Parola-123" }));

    const error = (await authRequest("/api/v1/auth/login", {
      method: "POST",
      body: { identifier: "ada", password: "Gizli-Parola-123" }
    }).catch((e: unknown) => e)) as AuthApiError;
    expect(JSON.stringify({ m: error.message, f: error.fieldErrors, c: error.code })).not.toContain("Gizli-Parola-123");
  });

  it("ağ hatasını NETWORK yapar ve mutasyonu tekrarlamaz", async () => {
    document.cookie = "XSRF-TOKEN=tok; path=/";
    const fn = vi.fn().mockRejectedValue(new TypeError("Failed to fetch"));
    vi.stubGlobal("fetch", fn);

    await expect(authRequest("/api/v1/auth/login", { method: "POST", body: {} })).rejects.toMatchObject({
      status: 0,
      code: "NETWORK"
    });
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it("kullanıcı iptalini (AbortError) olduğu gibi bırakır", async () => {
    const abort = new DOMException("iptal", "AbortError");
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(abort));

    await expect(authRequest("/api/v1/auth/me")).rejects.toBe(abort);
  });

  it("204'te undefined döner", async () => {
    document.cookie = "XSRF-TOKEN=tok; path=/";
    mockFetch(() => reply(204));
    await expect(authRequest("/api/v1/auth/session/logout", { method: "POST" })).resolves.toBeUndefined();
  });
});
