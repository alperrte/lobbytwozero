import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError, apiRequest } from "./client";

/**
 * `apiRequest`'in OTURUM YENİLEMESİ (tüm servisler için ortak): access token kısa ömürlüdür, 401'de ve oturum ipucu çerezi
 * varsa `POST /session/refresh` çağrılır, istek BİR kez tekrarlanır. Auth olmayan yollar (ör. `/api/v1/game/...`) dahil.
 */
const BASE = "http://localhost:5080";
const reply = (status: number, body?: unknown) =>
  new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" }
  });

function clearCookies() {
  for (const part of document.cookie.split(";")) {
    const name = part.split("=")[0]?.trim();
    if (name) {
      document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
    }
  }
}

/** Yanıtları sırayla verir; `/csrf` çerezi yazar (gerçek Gateway gibi). Çağrılan yolları döndürür. */
function mockFetch(handler: (path: string, call: number, init?: RequestInit) => Response) {
  let call = 0;
  const fn = vi.fn(async (input: string, init?: RequestInit) => {
    const path = input.replace(BASE, "");
    if (path.endsWith("/csrf")) {
      document.cookie = "XSRF-TOKEN=tok-1; path=/";
    }
    return handler(path, call++, init);
  });
  vi.stubGlobal("fetch", fn);
  return fn;
}
const calls = (fn: ReturnType<typeof vi.fn>) => fn.mock.calls.map((c) => String(c[0]).replace(BASE, ""));

beforeEach(() => {
  clearCookies();
  document.cookie = "ltz_session=1; path=/";
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
  clearCookies();
});

describe("apiRequest: otomatik oturum yenileme", () => {
  it("Auth olmayan bir yolda da 401 gelince yeniler ve isteği bir kez tekrarlar", async () => {
    let served = 0;
    const fn = mockFetch((path) => {
      if (path === "/api/v1/game/developer/games") return served++ === 0 ? reply(401) : reply(200, [{ id: 1 }]);
      return reply(204); // csrf, refresh
    });

    await expect(apiRequest("/api/v1/game/developer/games")).resolves.toEqual([{ id: 1 }]);

    expect(calls(fn)).toEqual([
      "/api/v1/game/developer/games",
      "/api/v1/auth/csrf",
      "/api/v1/auth/session/refresh",
      "/api/v1/game/developer/games"
    ]);
    const refresh = fn.mock.calls.find((c) => String(c[0]).endsWith("/session/refresh"));
    expect(refresh?.[1]).toMatchObject({ method: "POST", credentials: "include" });
    expect((refresh?.[1] as RequestInit).headers).toMatchObject({ "X-XSRF-TOKEN": "tok-1" });
  });

  it("oturum ipucu çerezi yoksa (misafir) yenilemeyi hiç denemez", async () => {
    clearCookies();
    const fn = mockFetch(() => reply(401));

    await expect(apiRequest("/api/v1/game/games")).rejects.toMatchObject({ status: 401 });

    expect(calls(fn)).toEqual(["/api/v1/game/games"]);
  });

  it("Auth'un kimlik uçlarında (login, kayıt, çıkış, yenileme, csrf) 401'de yenilemez", async () => {
    for (const path of [
      "/api/v1/auth/login",
      "/api/v1/auth/register",
      "/api/v1/auth/session/logout",
      "/api/v1/auth/session/refresh",
      "/api/v1/auth/csrf"
    ] as const) {
      const fn = mockFetch(() => reply(401));
      await expect(apiRequest(path)).rejects.toMatchObject({ status: 401 });
      expect(calls(fn), path).toEqual([path]);
    }
  });

  it("sorgu dizesi yenilemeyi engellemez; kimlik uçlarında ise sorgu olsa da yenilenmez", async () => {
    let served = 0;
    const fn = mockFetch((path) => (path.startsWith("/api/v1/game/games?") && served++ === 0 ? reply(401) : reply(200, [])));
    await expect(apiRequest("/api/v1/game/games?page=2")).resolves.toEqual([]);
    expect(calls(fn)).toContain("/api/v1/auth/session/refresh");

    const login = mockFetch(() => reply(401));
    await expect(apiRequest("/api/v1/auth/login?x=1")).rejects.toMatchObject({ status: 401 });
    expect(calls(login)).toEqual(["/api/v1/auth/login?x=1"]);
  });

  it("yenileme başarısızsa (401) özgün 401'i fırlatır ve tekrar denemez", async () => {
    const fn = mockFetch(() => reply(401));

    await expect(apiRequest("/api/v1/game/games")).rejects.toMatchObject({ status: 401 });

    expect(calls(fn).filter((p) => p === "/api/v1/game/games")).toHaveLength(1);
    expect(calls(fn).filter((p) => p.endsWith("/session/refresh"))).toHaveLength(1);
  });

  it("tekrarlanan istek yine 401 verirse ikinci kez yenilemez", async () => {
    const fn = mockFetch((path) => (path === "/api/v1/game/games" ? reply(401) : reply(204)));

    await expect(apiRequest("/api/v1/game/games")).rejects.toBeInstanceOf(ApiHttpError);

    expect(calls(fn).filter((p) => p.endsWith("/session/refresh"))).toHaveLength(1);
    expect(calls(fn).filter((p) => p === "/api/v1/game/games")).toHaveLength(2);
  });

  it("eşzamanlı 401'lerde tek yenileme yürütür", async () => {
    const seen = new Set<string>();
    const fn = mockFetch((path) => {
      if (path.startsWith("/api/v1/game/")) {
        if (!seen.has(path)) {
          seen.add(path);
          return reply(401);
        }
        return reply(200, { ok: true });
      }
      return reply(204);
    });

    await Promise.all([apiRequest("/api/v1/game/a"), apiRequest("/api/v1/game/b"), apiRequest("/api/v1/game/c")]);

    expect(calls(fn).filter((p) => p.endsWith("/session/refresh"))).toHaveLength(1);
  });

  it("REFRESH_CONFLICT'te kısa bekleyip isteği bir kez tekrarlar", async () => {
    vi.useFakeTimers();
    let served = 0;
    const fn = mockFetch((path) => {
      if (path === "/api/v1/game/games") return served++ === 0 ? reply(401) : reply(200, { ok: true });
      if (path.endsWith("/session/refresh")) return reply(409, { code: "REFRESH_CONFLICT" });
      return reply(204);
    });

    const request = apiRequest("/api/v1/game/games");
    await vi.advanceTimersByTimeAsync(300);

    await expect(request).resolves.toEqual({ ok: true });
    expect(calls(fn).filter((p) => p === "/api/v1/game/games")).toHaveLength(2);
  });

  it("CSRF reddedilirse çerezi yenileyip yenilemeyi bir kez daha dener", async () => {
    let refreshes = 0;
    let served = 0;
    const fn = mockFetch((path) => {
      if (path === "/api/v1/game/games") return served++ === 0 ? reply(401) : reply(200, { ok: true });
      if (path.endsWith("/session/refresh")) return refreshes++ === 0 ? reply(403, { code: "CSRF_TOKEN_INVALID" }) : reply(204);
      return reply(204);
    });

    await expect(apiRequest("/api/v1/game/games")).resolves.toEqual({ ok: true });

    expect(calls(fn).filter((p) => p.endsWith("/session/refresh"))).toHaveLength(2);
  });

  it("skipSessionRefresh açıkken 401'de yenilemez", async () => {
    const fn = mockFetch(() => reply(401));

    await expect(apiRequest("/api/v1/game/games", { skipSessionRefresh: true })).rejects.toMatchObject({ status: 401 });

    expect(calls(fn)).toEqual(["/api/v1/game/games"]);
  });

  it("ağ hatasında yenilemeyi denemez; hatayı olduğu gibi fırlatır", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));

    await expect(apiRequest("/api/v1/game/games")).rejects.toBeInstanceOf(TypeError);
  });

  it("yenileme isteği ağ hatasıyla düşerse özgün 401'i fırlatır (istisna sızdırmaz)", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: string) => {
        if (input.endsWith("/session/refresh") || input.endsWith("/csrf")) throw new TypeError("Failed to fetch");
        return reply(401);
      })
    );

    await expect(apiRequest("/api/v1/game/games")).rejects.toMatchObject({ status: 401 });
  });
});
