import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError, apiRequest } from "@/shared/lib/api/client";
import { libraryApi, loadLibraryResource, mutateLibrary } from "./library-api";
import type { LibraryMutation, LibraryPrivacy } from "../types/library";

vi.mock("@/shared/lib/api/client", async (original) => ({ ...await original<typeof import("@/shared/lib/api/client")>(), apiRequest: vi.fn() }));
const request = vi.mocked(apiRequest);
const id = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const signal = new AbortController().signal;
const settings: LibraryPrivacy = { version: 7, friendsList: "FRIENDS", library: "PUBLIC", hardware: "PRIVATE", performance: "FRIENDS", guestProfileAccess: "DETAILED" };
beforeEach(() => { document.cookie = "XSRF-TOKEN=library-test-csrf; path=/"; });
afterEach(() => { vi.resetAllMocks(); document.cookie = "XSRF-TOKEN=; max-age=0; path=/"; });

describe("Library Gateway adaptörü", () => {
  it("zenginleştirilmiş listeyi tek çağrıyla alır; false filtrelerini ve toplamı korur", async () => {
    const page = { items: [], page: 2, size: 50, totalElements: 101, metadataPartial: true };
    request.mockResolvedValue(page);
    expect(await libraryApi.list({ favorite: false, completed: true, sort: "TITLE", direction: "ASC", page: 2, size: 50 }, signal)).toEqual(page);
    expect(request).toHaveBeenCalledExactlyOnceWith("/api/v1/library/entries?sort=TITLE&direction=ASC&page=2&size=50&favorite=false&completed=true", expect.objectContaining({ signal, cache: "no-store" }));
  });
  it("istatistik yalnız gerçek sayaç endpointinden gelir", async () => {
    request.mockResolvedValue({ totalGames: 10, favoriteGames: 3, completedGames: 4 });
    expect(await loadLibraryResource({ kind: "statistics" }, signal)).toEqual({ totalGames: 10, favoriteGames: 3, completedGames: 4 });
    expect(request).toHaveBeenCalledWith("/api/v1/library/entries/statistics", expect.anything());
  });
  it("izinli profil listesinden sonra istatistik sorgular", async () => {
    request.mockResolvedValueOnce({ items: [], page: 1, size: 10, totalElements: 0 }).mockResolvedValueOnce({ totalGames: 0 });
    expect(await loadLibraryResource({ kind: "profile", userId: id, page: 1, size: 10 }, signal)).toMatchObject({ collection: { items: [] }, statistics: { totalGames: 0 } });
    expect(request).toHaveBeenNthCalledWith(1, `/api/v1/library/users/${id}/entries?page=1&size=10`, expect.anything());
    expect(request).toHaveBeenNthCalledWith(2, `/api/v1/library/users/${id}/entries/statistics`, expect.anything());
  });
  it.each([403, 503])("profil listesi %s ise sayaç sorgulamaz", async (status) => {
    request.mockRejectedValue(new ApiHttpError(status));
    await expect(loadLibraryResource({ kind: "profile", userId: id, page: 0, size: 20 }, signal)).rejects.toMatchObject({ status });
    expect(request).toHaveBeenCalledTimes(1);
  });
  it("ikinci profil erişim kontrolü reddedilirse listeyi de döndürmez", async () => {
    request.mockResolvedValueOnce({ items: [{ secret: "hidden" }] }).mockRejectedValueOnce(new ApiHttpError(403));
    await expect(loadLibraryResource({ kind: "profile", userId: id, page: 0, size: 20 }, signal)).rejects.toMatchObject({ status: 403 });
  });
  it.each([
    [{ kind: "add", gameId: id }, "POST", "/api/v1/library/entries"],
    [{ kind: "remove", gameId: id }, "DELETE", `/api/v1/library/entries/${id}`],
    [{ kind: "favorite", gameId: id, enabled: true }, "PUT", `/api/v1/library/entries/${id}/favorite`],
    [{ kind: "favorite", gameId: id, enabled: false }, "DELETE", `/api/v1/library/entries/${id}/favorite`],
    [{ kind: "completed", gameId: id, enabled: true }, "PUT", `/api/v1/library/entries/${id}/completed`],
    [{ kind: "completed", gameId: id, enabled: false }, "DELETE", `/api/v1/library/entries/${id}/completed`]
  ] as const)("%j doğru method/yola CSRF ile gönderilir", async (action, method, path) => {
    request.mockResolvedValue(undefined);
    expect(await mutateLibrary(action as LibraryMutation, signal)).toBeUndefined();
    expect(request).toHaveBeenCalledExactlyOnceWith(path, expect.objectContaining({ method, signal, cache: "no-store", headers: expect.objectContaining({ "X-XSRF-TOKEN": "library-test-csrf" }) }));
    if (action.kind === "add") expect(request.mock.calls[0][1]?.body).toBe(JSON.stringify({ gameId: id }));
  });
  it("idempotent ekleme yanıtına yerel sayaç veya kayıt eklemez", async () => {
    const response = { id: "entry", gameId: id, favorite: true, version: 3 };
    request.mockResolvedValue(response);
    expect(await mutateLibrary({ kind: "add", gameId: id }, signal)).toBe(response);
    expect(await mutateLibrary({ kind: "add", gameId: id }, signal)).toBe(response);
  });
  it("gizlilik kaydında diğer alanlar ve version aynen korunur", async () => {
    request.mockResolvedValue({ ...settings, library: "PRIVATE", version: 8 });
    await mutateLibrary({ kind: "privacy", settings, library: "PRIVATE" }, signal);
    expect(request.mock.calls[0][0]).toBe("/api/v1/user/me/privacy");
    expect(JSON.parse(String(request.mock.calls[0][1]?.body))).toEqual({ ...settings, library: "PRIVATE" });
    expect(settings.library).toBe("PUBLIC");
  });
  it("CSRF eksikse önce güvenli Auth ucu üzerinden edinir", async () => {
    document.cookie = "XSRF-TOKEN=; max-age=0; path=/";
    request.mockImplementation(async (path) => { if (path === "/api/v1/auth/csrf") document.cookie = "XSRF-TOKEN=renewed; path=/"; return undefined; });
    await mutateLibrary({ kind: "remove", gameId: id }, signal);
    expect(request).toHaveBeenNthCalledWith(1, "/api/v1/auth/csrf", expect.anything());
    expect(request.mock.calls[1][1]?.headers).toMatchObject({ "X-XSRF-TOKEN": "renewed" });
  });
  it("CSRF reddinde yalnız bir kontrollü yenileme yapar", async () => {
    request.mockRejectedValueOnce(new ApiHttpError(403, { code: "CSRF_TOKEN_INVALID" })).mockResolvedValueOnce({}).mockRejectedValueOnce(new ApiHttpError(403, { code: "CSRF_TOKEN_INVALID" }));
    await expect(mutateLibrary({ kind: "add", gameId: id }, signal)).rejects.toMatchObject({ status: 403 });
    expect(request).toHaveBeenCalledTimes(3);
  });
  it.each([400, 401, 403, 404, 409, 429, 500, 503])("%s mutasyon hatasında kör retry yapmaz", async (status) => {
    request.mockRejectedValue(new ApiHttpError(status));
    await expect(mutateLibrary({ kind: "add", gameId: id }, signal)).rejects.toMatchObject({ status });
    expect(request).toHaveBeenCalledTimes(1);
  });
  it("ağ hatasında mutasyonu tekrar göndermez", async () => {
    request.mockRejectedValue(new TypeError("offline"));
    await expect(mutateLibrary({ kind: "remove", gameId: id }, signal)).rejects.toThrow();
    expect(request).toHaveBeenCalledTimes(1);
  });
  it("geçersiz kimlikle HTTP çağrısı yapmaz", async () => {
    await expect(mutateLibrary({ kind: "add", gameId: "730" }, signal)).rejects.toMatchObject({ status: 400 });
    expect(request).not.toHaveBeenCalled();
  });
});
