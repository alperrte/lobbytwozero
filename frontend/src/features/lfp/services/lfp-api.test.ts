import { beforeEach, describe, expect, it, vi } from "vitest";

const lfpRequest = vi.fn();
const apiRequest = vi.fn();
vi.mock("./lfp-http", () => ({ lfpRequest: (...args: unknown[]) => lfpRequest(...args) }));
vi.mock("@/shared/lib/api/client", async (original) => ({
  ...(await original<typeof import("@/shared/lib/api/client")>()),
  apiRequest: (...args: unknown[]) => apiRequest(...args)
}));

const { fetchGameCards, lfpApi, playWindow } = await import("./lfp-api");

const L = "11111111-1111-4111-8111-111111111111";
const G = "22222222-2222-4222-8222-222222222222";

beforeEach(() => {
  lfpRequest.mockReset();
  lfpRequest.mockResolvedValue(undefined);
  apiRequest.mockReset();
  apiRequest.mockResolvedValue({ items: [] });
});

const calls = () => lfpRequest.mock.calls.map(([path, options]) => `${options?.method ?? "GET"} ${path}`);

describe("lfpApi uç eşlemesi (docs/api.md → LFP)", () => {
  it("arama süzgeçlerini ve sayfalamayı sorguya yazar; boş süzgeç gönderilmez", async () => {
    await lfpApi.search({ q: "ranked %", gameId: G, platform: "PC", skillLevel: "Altın", role: "Destek", micRequired: false }, { limit: 12, cursor: "c+/=" });
    await lfpApi.search({});
    expect(calls()).toEqual([
      `GET /api/v1/lfp/listings?q=ranked+%25&gameId=${G}&platform=PC&skillLevel=Alt%C4%B1n&role=Destek&micRequired=false&limit=12&cursor=c%2B%2F%3D`,
      "GET /api/v1/lfp/listings"
    ]);
  });

  it("zaman ön ayarı playFrom/playTo aralığına çevrilir", async () => {
    await lfpApi.search({ when: "tomorrow" });
    const url = new URL(`http://x${lfpRequest.mock.calls[0][0]}`);
    expect(url.searchParams.get("playFrom")).toMatch(/Z$/);
    expect(url.searchParams.get("playTo")).toMatch(/Z$/);
    expect(url.searchParams.has("when")).toBe(false);
  });

  it("ilanlarım, detay, oluştur, güncelle, kapat ve iletişim", async () => {
    await lfpApi.mine({ status: "OPEN", limit: 50 });
    await lfpApi.get(L);
    await lfpApi.create({ gameId: G, platform: "PC", roles: ["Destek"], playersNeeded: 2, micRequired: true, description: "x", durationHours: 24 });
    await lfpApi.update(L, { platform: "XBOX", roles: ["Destek"], playersNeeded: 1, micRequired: false, description: "y", version: 3 });
    await lfpApi.close(L);
    await lfpApi.contact("../x");
    expect(calls()).toEqual([
      "GET /api/v1/lfp/listings/mine?status=OPEN&limit=50",
      `GET /api/v1/lfp/listings/${L}`,
      "POST /api/v1/lfp/listings",
      `PUT /api/v1/lfp/listings/${L}`,
      `POST /api/v1/lfp/listings/${L}/close`,
      "POST /api/v1/lfp/listings/..%2Fx/contact"
    ]);
    expect(lfpRequest.mock.calls[3][1].body).toEqual({ platform: "XBOX", roles: ["Destek"], playersNeeded: 1, micRequired: false, description: "y", version: 3 });
  });

  it("oyun kapakları tek toplu istekle, çerezsiz ve en çok 50 kimlikle istenir", async () => {
    const ids = Array.from({ length: 60 }, (_, index) => `00000000-0000-4000-8000-${String(index).padStart(12, "0")}`);
    await fetchGameCards([...ids, ids[0]]);
    expect(apiRequest).toHaveBeenCalledTimes(1);
    const [path, options] = apiRequest.mock.calls[0];
    expect(path).toBe("/api/v1/game/games/batch");
    expect(options).toMatchObject({ method: "POST", credentials: "omit" });
    expect(JSON.parse(options.body).gameIds).toHaveLength(50);
    await expect(fetchGameCards([])).resolves.toEqual({ items: [] });
    expect(apiRequest).toHaveBeenCalledTimes(1);
  });
});

describe("playWindow", () => {
  const now = new Date(2026, 10, 24, 15, 30);

  it("bugün: şimdiden gün sonuna; yarın: yarının tamamı; hafta: şimdiden 7 gün", () => {
    const today = playWindow("today", now);
    expect(new Date(today.playFrom!).getTime()).toBe(now.getTime());
    expect(new Date(today.playTo!).getDate()).toBe(24);
    const tomorrow = playWindow("tomorrow", now);
    expect(new Date(tomorrow.playFrom!)).toEqual(new Date(2026, 10, 25, 0, 0));
    expect(new Date(tomorrow.playTo!).getDate()).toBe(25);
    const week = playWindow("week", now);
    expect(new Date(week.playTo!).getDate()).toBe(30);
    expect(playWindow(undefined, now)).toEqual({});
  });
});
