import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError, apiRequest } from "@/shared/lib/api/client";
import { loadGameResource } from "./game-api";

vi.mock("@/shared/lib/api/client", async (original) => ({ ...await original<typeof import("@/shared/lib/api/client")>(), apiRequest: vi.fn() }));
const gameId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const signal = new AbortController().signal;
const request = vi.mocked(apiRequest);
afterEach(() => vi.resetAllMocks());

describe("Game ekranının Community entegrasyonu", () => {
  it.each(["GUIDES", "EASTER_EGGS", "EXPERIENCES"] as const)("%s için yalnız sürümlü modül ve tek liste çağrısı yapar", async (type) => {
    const path = `/api/v1/community/games/${gameId}/content?type=${type}&page=2&size=20`;
    request.mockResolvedValueOnce({ state: "EXTERNAL", contentSource: { service: "COMMUNITY", listPath: path } });
    request.mockResolvedValueOnce({ items: [], totalElements: 12, page: 2, size: 20 });
    const result = await loadGameResource({ kind: "module", gameId, type, page: 2 }, signal);
    expect(result).toMatchObject({ kind: "community", totalElements: 12, items: [] });
    expect(request).toHaveBeenNthCalledWith(1, `/api/v1/game/games/${gameId}/modules/${type}?contractVersion=2&page=2&size=20`, { signal, cache: "no-store" });
    expect(request).toHaveBeenNthCalledWith(2, path, { signal, cache: "no-store" });
    expect(request).toHaveBeenCalledTimes(2);
  });
  it("başarımlarda Community'ye çağrı yapmaz", async () => {
    request.mockResolvedValueOnce({ state: "EMPTY", items: [], totalElements: 0 });
    expect(await loadGameResource({ kind: "module", gameId, type: "ACHIEVEMENTS", page: 0 }, signal)).toMatchObject({ kind: "achievements", totalElements: 0 });
    expect(request).toHaveBeenCalledTimes(1);
  });
  it.each([undefined, { service: "COMMUNITY", listPath: "https://untrusted.invalid" }, { service: "CONTENT", listPath: "/api/v1/content" }])("bekleyen veya sözleşme dışı kaynağa istek atmaz", async (contentSource) => {
    request.mockResolvedValueOnce({ state: "EXTERNAL", contentSource });
    await expect(loadGameResource({ kind: "module", gameId, type: "GUIDES", page: 0 }, signal)).rejects.toThrow("MODULE_SOURCE_UNAVAILABLE");
    expect(request).toHaveBeenCalledTimes(1);
  });
  it("sağlayıcı kesintisini boş listeye çevirmez", async () => {
    request.mockResolvedValueOnce({ state: "EXTERNAL", contentSource: { service: "COMMUNITY", listPath: `/api/v1/community/games/${gameId}/content?type=GUIDES&page=0&size=20` } });
    request.mockRejectedValueOnce(new Error("unavailable"));
    await expect(loadGameResource({ kind: "module", gameId, type: "GUIDES", page: 0 }, signal)).rejects.toThrow("unavailable");
    expect(request).toHaveBeenCalledTimes(2);
  });
  it("içerik detayını aynı oyun altında okur ve geçersiz kimliği reddeder", async () => {
    await loadGameResource({ kind: "content", gameId, contentId: gameId }, signal);
    expect(request).toHaveBeenCalledWith(`/api/v1/community/games/${gameId}/content/${gameId}`, { signal, cache: "no-store" });
    await expect(loadGameResource({ kind: "content", gameId, contentId: "../private" }, signal)).rejects.toThrow("INVALID_CONTENT_ID");
    expect(request).toHaveBeenCalledTimes(1);
  });
  it("toplu kart isteği çerezsiz ve tekilleştirilmiş gider (çerezli POST Gateway'de CSRF ister)", async () => {
    request.mockResolvedValueOnce({ items: [], unavailable: [] });
    await loadGameResource({ kind: "cards", gameIds: [gameId, gameId] }, new AbortController().signal);
    expect(request).toHaveBeenCalledWith("/api/v1/game/games/batch", expect.objectContaining({ method: "POST", credentials: "omit", body: JSON.stringify({ gameIds: [gameId] }), headers: { "Content-Type": "application/json" } }));
  });
  it("toplu kartta boş, 50'den fazla veya geçersiz kimlikleri istek atmadan reddeder", async () => {
    const signal = new AbortController().signal;
    await expect(loadGameResource({ kind: "cards", gameIds: [] }, signal)).rejects.toThrow("INVALID_GAME_ID");
    await expect(loadGameResource({ kind: "cards", gameIds: ["x"] }, signal)).rejects.toThrow("INVALID_GAME_ID");
    await expect(loadGameResource({ kind: "cards", gameIds: Array.from({ length: 51 }, (_, n) => `0198a3c2-0000-7d44-9a55-${String(n).padStart(12, "0")}`) }, signal)).rejects.toThrow("INVALID_GAME_ID");
    expect(request).not.toHaveBeenCalled();
  });
  it("eşzamanlı oturum istekleri tek /auth/me çağrısını paylaşır; bitince sonraki çağrı yeniden sorar (önbellek yok)", async () => {
    const accountId = "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66";
    request.mockResolvedValue({ accountId, roles: ["PLAYER"] });
    const signal = new AbortController().signal;
    const [first, second] = await Promise.all([loadGameResource({ kind: "session" }, signal), loadGameResource({ kind: "session" }, signal)]);
    expect(first).toEqual(second);
    expect(request).toHaveBeenCalledTimes(1);
    await loadGameResource({ kind: "session" }, signal);
    expect(request).toHaveBeenCalledTimes(2);
  });
  it("oturum yoksa (401) null döner; geçersiz yanıtı reddeder", async () => {
    const signal = new AbortController().signal;
    request.mockRejectedValueOnce(new ApiHttpError(401));
    await expect(loadGameResource({ kind: "session" }, signal)).resolves.toBeNull();
    request.mockResolvedValueOnce({ accountId: "x", roles: [] });
    await expect(loadGameResource({ kind: "session" }, signal)).rejects.toThrow("INVALID_SESSION_RESPONSE");
  });
});
