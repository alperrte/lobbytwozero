import { afterEach, describe, expect, it, vi } from "vitest";
import { enrichmentApi, safeAvatarPath, toConsent } from "./enrichment-api";

const G1 = "11111111-1111-4111-8111-111111111111";
const G2 = "22222222-2222-4222-8222-222222222222";

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const path = (fetch: ReturnType<typeof vi.fn>, index = 0) => String(fetch.mock.calls[index][0]).replace(/^.*\/api\/v1/, "/api/v1");

afterEach(() => {
  vi.unstubAllGlobals();
  document.cookie = "XSRF-TOKEN=; max-age=0; path=/";
});

describe("zenginleştirme uçları", () => {
  it("oyun kartları çerezsiz, tekil ve yalnız UUID kimliklerle istenir; bozuk kayıt atlanır", async () => {
    const fetch = vi.fn().mockResolvedValue(json({ items: [{ gameId: G1.toUpperCase(), title: "One", coverUrl: null }, { gameId: G2 }] }));
    vi.stubGlobal("fetch", fetch);
    const cards = await enrichmentApi.gameCards([G1, G1, "not-uuid", G2]);
    expect(path(fetch)).toBe("/api/v1/game/games/batch");
    expect(fetch.mock.calls[0][1]).toMatchObject({ method: "POST", credentials: "omit", cache: "no-store" });
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ gameIds: [G1, G2] });
    expect([...cards.keys()]).toEqual([G1]);
  });

  it("boş kimlik listesinde ağa çıkmaz", async () => {
    const fetch = vi.fn();
    vi.stubGlobal("fetch", fetch);
    expect((await enrichmentApi.gameCards(["x"])).size).toBe(0);
    expect((await enrichmentApi.profileCards([])).size).toBe(0);
    expect(await enrichmentApi.searchCatalog("   ")).toEqual([]);
    expect(fetch).not.toHaveBeenCalled();
  });

  it("profil kartları CSRF ile gider; güvensiz avatar adresi atılır", async () => {
    document.cookie = "XSRF-TOKEN=test-csrf; path=/";
    const fetch = vi.fn().mockResolvedValue(json({ items: [{ userId: G1, username: "one", displayName: null, avatar: { small: "https://evil.example/a.png" } }] }));
    vi.stubGlobal("fetch", fetch);
    const cards = await enrichmentApi.profileCards([G1]);
    expect(path(fetch)).toBe("/api/v1/user/profiles/batch");
    expect(fetch.mock.calls[0][1]).toMatchObject({ method: "POST", headers: { "X-XSRF-TOKEN": "test-csrf" } });
    expect(cards.get(G1)).toEqual({ userId: G1, username: "one", displayName: null, avatarPath: null });
  });

  it("rıza kaydı yalnız sürüm ve öneri izni gönderir", async () => {
    document.cookie = "XSRF-TOKEN=test-csrf; path=/";
    const fetch = vi.fn().mockResolvedValue(json({ recommendation: true, version: 4, marketing: false }));
    vi.stubGlobal("fetch", fetch);
    expect(await enrichmentApi.saveConsent({ recommendation: true, version: 3 })).toEqual({ recommendation: true, version: 4 });
    expect(path(fetch)).toBe("/api/v1/user/me/consents");
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ version: 3, recommendation: true });
  });

  it("arkadaşlık isteği ve etkinlik katılımı doğru uçlara gider", async () => {
    document.cookie = "XSRF-TOKEN=test-csrf; path=/";
    const fetch = vi.fn().mockResolvedValueOnce(json({}, 201)).mockResolvedValueOnce(json({ id: "p", eventId: "e/1", status: "JOINED" }, 201));
    vi.stubGlobal("fetch", fetch);
    await enrichmentApi.sendFriendRequest(G1);
    expect(path(fetch, 0)).toBe("/api/v1/social/friend-requests");
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ receiverId: G1 });
    await enrichmentApi.joinEvent("e/1");
    expect(path(fetch, 1)).toBe("/api/v1/event/events/e%2F1/participations");
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({});
  });

  it("katalog araması sorguyu 128 karakterle sınırlar", async () => {
    const fetch = vi.fn().mockResolvedValue(json({ items: [{ gameId: G1, title: "One" }, { gameId: "x", title: "Bad" }] }));
    vi.stubGlobal("fetch", fetch);
    expect(await enrichmentApi.searchCatalog(`  ${"a".repeat(200)} `)).toEqual([{ gameId: G1, title: "One" }]);
    const url = new URL(String(fetch.mock.calls[0][0]), "http://localhost");
    expect(url.searchParams.get("q")).toHaveLength(128);
    expect(url.searchParams.get("size")).toBe("20");
  });
});

describe("yardımcılar", () => {
  it.each([
    ["/api/v1/user/media/0123456789abcdef0123456789abcdef-s.png", true],
    ["/api/v1/user/media/0123456789abcdef0123456789abcdef.jpg", true],
    ["/api/v1/user/media/../../secret.png", false],
    ["https://cdn.example.com/a.png", false],
    ["javascript:alert(1)", false]
  ])("safeAvatarPath(%s)", (value, ok) => {
    expect(safeAvatarPath(value)).toBe(ok ? value : null);
  });

  it("bilinmeyen rıza gövdesi izin yok sayılır", () => {
    expect(toConsent(null)).toEqual({ recommendation: false, version: 0 });
    expect(toConsent({ recommendation: "true", version: -1 })).toEqual({ recommendation: false, version: 0 });
    expect(toConsent({ recommendation: true, version: 7 })).toEqual({ recommendation: true, version: 7 });
  });
});
