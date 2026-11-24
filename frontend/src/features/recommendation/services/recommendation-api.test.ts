import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import { recommendationApi } from "./recommendation-api";

const RUN = {
  runId: "11111111-1111-4111-8111-111111111111",
  algorithmVersion: "rec-v1",
  createdAt: "2026-11-20T10:00:00Z",
  status: "PARTIAL",
  genre: null,
  platform: null,
  items: [],
  limitations: []
};

function json(body: unknown, init: ResponseInit = {}) {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" }, ...init });
}

function stub(...responses: Response[]) {
  const fetch = vi.fn();
  for (const response of responses) fetch.mockResolvedValueOnce(response);
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

const path = (fetch: ReturnType<typeof vi.fn>, index = 0) => String(fetch.mock.calls[index][0]).replace(/^.*\/api\/v1/, "/api/v1");

afterEach(() => {
  vi.unstubAllGlobals();
  document.cookie = "XSRF-TOKEN=; max-age=0; path=/";
});

describe("recommendationApi HTTP sözleşmesi", () => {
  it("POST /games yalnız dolu filtreleri ve sınırlanmış boyutu gönderir (CSRF başlığıyla)", async () => {
    document.cookie = "XSRF-TOKEN=test-csrf; path=/";
    const fetch = stub(json(RUN, { status: 201 }));
    await recommendationApi.generateGames({ genre: "", platform: " pc ", size: 500 });
    expect(path(fetch)).toBe("/api/v1/recommendation/games");
    const options = fetch.mock.calls[0][1];
    expect(options).toMatchObject({ method: "POST", cache: "no-store", credentials: "include", headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": "test-csrf" } });
    expect(JSON.parse(options.body)).toEqual({ size: 50, platform: "pc" });
  });

  it("desene uymayan tür kodu hiç gönderilmez", async () => {
    document.cookie = "XSRF-TOKEN=test-csrf; path=/";
    const fetch = stub(json(RUN, { status: 201 }));
    await recommendationApi.generateGames({ genre: "RPG; drop", size: 12 });
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ size: 12 });
  });

  it("GET okumaları no-store ve çerezle gider; boş sorgu parametreleri atlanır", async () => {
    const fetch = stub(json({ items: [], nextCursor: null }));
    await recommendationApi.listRuns({ size: 99 });
    expect(path(fetch)).toBe("/api/v1/recommendation/runs?size=20");
    expect(fetch.mock.calls[0][1]).toMatchObject({ cache: "no-store", credentials: "include" });
    expect(fetch.mock.calls[0][1].method).toBeUndefined();
  });

  it.each([
    ["getRun", () => recommendationApi.getRun("a/b"), "/api/v1/recommendation/runs/a%2Fb", RUN],
    ["getFeedback", () => recommendationApi.getFeedback("r", "i"), "/api/v1/recommendation/runs/r/items/i/feedback", { itemId: "i", value: null, version: 0 }],
    [
      "hardwareCompatible",
      () => recommendationApi.hardwareCompatible({ profileId: "p", platform: "windows", genre: "", size: 5 }),
      "/api/v1/recommendation/games/hardware-compatible?profileId=p&platform=windows&size=5",
      { algorithmVersion: "v", createdAt: "t", hardwareProfileId: "p", platform: "windows", items: [], excludedBelowMinimum: 0, limitations: [] }
    ],
    ["similarPlayers", () => recommendationApi.similarPlayers({ size: 4, genre: "rpg" }), "/api/v1/recommendation/players?size=4&genre=rpg", { kind: "SIMILAR_PLAYERS", algorithmVersion: "v", createdAt: "t", items: [], limitations: [] }],
    ["friendSuggestions", () => recommendationApi.friendSuggestions({ size: 12 }), "/api/v1/recommendation/friends?size=12", { kind: "FRIEND_SUGGESTIONS", algorithmVersion: "v", createdAt: "t", items: [], limitations: [] }],
    ["events", () => recommendationApi.events(3), "/api/v1/recommendation/events?size=3", { algorithmVersion: "v", createdAt: "t", items: [], limitations: [] }]
  ] as const)("%s doğru adresi okur", async (_name, action, expected, body) => {
    const fetch = stub(json(body));
    await action();
    expect(path(fetch)).toBe(expected);
  });

  it("PUT feedback gövdesi yalnız değer ve sürümdür", async () => {
    document.cookie = "XSRF-TOKEN=test-csrf; path=/";
    const fetch = stub(json({ itemId: "i", value: "LIKE", version: 4 }));
    const result = await recommendationApi.putFeedback("r", "i", "LIKE", 3);
    expect(result).toEqual({ itemId: "i", value: "LIKE", version: 4 });
    expect(fetch.mock.calls[0][1]).toMatchObject({ method: "PUT", headers: { "X-XSRF-TOKEN": "test-csrf" } });
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ feedback: "LIKE", version: 3 });
  });

  it("donanım değerlendirmesi isteğe bağlı alanları yalnız doluysa gönderir", async () => {
    document.cookie = "XSRF-TOKEN=test-csrf; path=/";
    const fetch = stub(new Response(JSON.stringify({}), { status: 200 }));
    await recommendationApi.assessHardware({ hardwareProfileId: "p", gameId: "g", platform: "windows", resolution: "", graphicsPreset: "HIGH" }).catch(() => undefined);
    expect(path(fetch)).toBe("/api/v1/recommendation/hardware/assessments");
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ hardwareProfileId: "p", gameId: "g", platform: "windows", graphicsPreset: "HIGH" });
  });

  it("CSRF çerezi yoksa önce /auth/csrf çağrılır; CSRF_TOKEN_INVALID bir kez tekrarlanır", async () => {
    let writes = 0;
    const fetch = vi.fn().mockImplementation(async (url: string) => {
      if (url.endsWith("/api/v1/auth/csrf")) {
        document.cookie = `XSRF-TOKEN=token-${writes}; path=/`;
        return new Response(null, { status: 204 });
      }
      writes += 1;
      return writes === 1 ? json({ code: "CSRF_TOKEN_INVALID" }, { status: 403 }) : json(RUN, { status: 201 });
    });
    vi.stubGlobal("fetch", fetch);
    await recommendationApi.generateGames({ size: 6 });
    expect(fetch.mock.calls.map(([url]) => String(url).replace(/^.*\/api\/v1/, ""))).toEqual(["/auth/csrf", "/recommendation/games", "/auth/csrf", "/recommendation/games"]);
    expect(fetch.mock.calls[3][1].headers).toMatchObject({ "X-XSRF-TOKEN": "token-1" });
  });

  it("ikinci CSRF reddinde sonsuz döngüye girmez", async () => {
    document.cookie = "XSRF-TOKEN=stale; path=/";
    const fetch = vi.fn().mockImplementation(async (url: string) => (url.endsWith("/api/v1/auth/csrf") ? new Response(null, { status: 204 }) : json({ code: "CSRF_TOKEN_INVALID" }, { status: 403 })));
    vi.stubGlobal("fetch", fetch);
    await expect(recommendationApi.generateGames({ size: 6 })).rejects.toMatchObject({ status: 403, code: "CSRF_TOKEN_INVALID" });
    expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/recommendation/games"))).toHaveLength(2);
  });

  it("CONSENT_REQUIRED ve Retry-After hata nesnesine taşınır", async () => {
    stub(json({ code: "CONSENT_REQUIRED" }, { status: 403 }));
    await expect(recommendationApi.events(3)).rejects.toMatchObject({ status: 403, code: "CONSENT_REQUIRED" });
    stub(json({ code: "RATE_LIMITED" }, { status: 429, headers: { "Retry-After": "60" } }));
    await expect(recommendationApi.events(3)).rejects.toMatchObject({ status: 429, retryAfterSeconds: 60 });
  });

  it("ağ hatası NETWORK kodlu 0 durumlu hataya dönüşür", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));
    const error = await recommendationApi.listRuns().catch((reason: unknown) => reason);
    expect(error).toBeInstanceOf(ApiHttpError);
    expect(error).toMatchObject({ status: 0, code: "NETWORK" });
  });

  it("iptal edilen istek ağ hatasına çevrilmez", async () => {
    const controller = new AbortController();
    vi.stubGlobal("fetch", vi.fn().mockImplementation(async () => {
      controller.abort();
      throw new DOMException("aborted", "AbortError");
    }));
    const error = await recommendationApi.listRuns({}, controller.signal).catch((reason: unknown) => reason);
    expect(error).toBeInstanceOf(DOMException);
  });

  it("sözleşmeye uymayan gövde INVALID_RESPONSE (502) olur", async () => {
    stub(json({ ...RUN, status: "DONE" }));
    await expect(recommendationApi.getRun("r")).rejects.toMatchObject({ status: 502, code: "INVALID_RESPONSE" });
  });
});
