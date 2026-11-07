import { afterEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "@/shared/lib/api/client";
import { loadGameResource } from "./game-api";

vi.mock("@/shared/lib/api/client", () => ({ apiRequest: vi.fn() }));
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
});
