import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError, apiRequest } from "@/shared/lib/api/client";
import { loadGameResource, mutateGame } from "./game-api";
import type { GameMutation } from "../types/game";

vi.mock("@/shared/lib/api/client", async (original) => ({ ...await original<typeof import("@/shared/lib/api/client")>(), apiRequest: vi.fn() }));
const request = vi.mocked(apiRequest);
const gameId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const targetId = "0198a3c2-9999-7d44-9a55-3f1c2b4d5e66";
beforeEach(() => { document.cookie = "XSRF-TOKEN=;Max-Age=0;Path=/"; });
afterEach(() => vi.resetAllMocks());
function csrf() {
  request.mockImplementationOnce(async () => { document.cookie = "XSRF-TOKEN=fixture-csrf;Path=/"; });
}

describe("Game çerezli mutasyonları", () => {
  it.each([
    ["create", "/api/v1/game/admin/games", "POST"], ["update", `/api/v1/game/admin/games/${gameId}`, "PATCH"],
    ["archive", `/api/v1/game/admin/games/${gameId}/archive`, "POST"], ["restore", `/api/v1/game/admin/games/${gameId}/restore`, "POST"],
    ["grant", `/api/v1/game/admin/games/${gameId}/developers`, "POST"], ["revoke", `/api/v1/game/admin/games/${gameId}/developers/${targetId}/revoke`, "POST"],
    ["description", `/api/v1/game/developer/games/${gameId}/description`, "PUT"], ["addMedia", `/api/v1/game/developer/games/${gameId}/media`, "POST"],
    ["deleteMedia", `/api/v1/game/developer/games/${gameId}/media/${targetId}`, "DELETE"], ["addNote", `/api/v1/game/developer/games/${gameId}/update-notes`, "POST"],
    ["updateNote", `/api/v1/game/developer/games/${gameId}/update-notes/${targetId}`, "PUT"], ["deleteNote", `/api/v1/game/developer/games/${gameId}/update-notes/${targetId}`, "DELETE"],
    ["feedback", "/api/v1/game/feedback", "POST"], ["feedbackStatus", `/api/v1/game/developer/games/${gameId}/feedback/${targetId}/status`, "PATCH"],
    ["moderate", `/api/v1/game/moderation/games/${gameId}/developer-content/actions`, "POST"], ["popular", "/api/v1/game/admin/popular", "PUT"]
  ] as const)("%s için önce CSRF alır ve sözleşmeli yolu kullanır", async (operation, path, method) => {
    csrf(); request.mockResolvedValueOnce({ version: 8 });
    await mutateGame({ operation, gameId, targetId, body: { version: 7 } });
    expect(request).toHaveBeenNthCalledWith(1, "/api/v1/auth/csrf", expect.objectContaining({ cache: "no-store" }));
    expect(request).toHaveBeenNthCalledWith(2, path, expect.objectContaining({ method, cache: "no-store", headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": "fixture-csrf" }, body: '{"version":7}' }));
    expect(request).toHaveBeenCalledTimes(2);
  });
  it("CSRF çerezi oluşmadıysa yazma isteği göndermez", async () => {
    request.mockResolvedValueOnce(undefined);
    await expect(mutateGame({ operation: "create", body: { title: "Fixture" } })).rejects.toThrow("CSRF_UNAVAILABLE");
    expect(request).toHaveBeenCalledTimes(1);
  });
  it("503 alan mutasyonu otomatik tekrarlamaz", async () => {
    csrf(); request.mockRejectedValueOnce(new ApiHttpError(503));
    await expect(mutateGame({ operation: "create" })).rejects.toMatchObject({ status: 503 });
    expect(request).toHaveBeenCalledTimes(2);
  });
  it.each([{ operation: "deleteMedia", gameId, targetId: "../private" }, { operation: "release", gameId, field: "../private" }, { operation: "update", gameId: "../private" }] as GameMutation[])("geçersiz hedefe ağ isteği atmaz", async (action) => {
    await expect(mutateGame(action)).rejects.toThrow(); expect(request).not.toHaveBeenCalled();
  });
  it("başarım güncellemesinde kodu, manuel kilit açmada alanı doğrular", async () => {
    csrf(); await mutateGame({ operation: "achievement", gameId, targetId: "first-win", body: { version: 0 } });
    expect(request).toHaveBeenLastCalledWith(`/api/v1/game/admin/games/${gameId}/achievements/first-win`, expect.objectContaining({ method: "PUT" }));
    csrf(); await mutateGame({ operation: "release", gameId, field: "TITLE", body: { reason: "Fixture reason" } });
    expect(request).toHaveBeenLastCalledWith(`/api/v1/game/admin/games/${gameId}/manual-overrides/TITLE/release`, expect.objectContaining({ method: "POST" }));
  });
  it("misafir oturumunu 401 ile tanır; Auth kesintisini misafir gibi göstermez", async () => {
    const signal = new AbortController().signal;
    request.mockRejectedValueOnce(new ApiHttpError(401));
    expect(await loadGameResource({ kind: "session" }, signal)).toBeNull();
    request.mockRejectedValueOnce(new ApiHttpError(503));
    await expect(loadGameResource({ kind: "session" }, signal)).rejects.toMatchObject({ status: 503 });
  });
});
