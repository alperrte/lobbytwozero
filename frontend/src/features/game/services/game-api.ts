import { ApiHttpError, apiRequest } from "@/shared/lib/api/client";
import type { CatalogStatus, CommunityDetail, CommunityPage, DiscoveryPage, GameDetail, GameModule, GameResource } from "../types/game";
import type { GameMutation, GameSession } from "../types/game";

export function isGameId(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}

/** Yalnız Game ekranının sözleşmeli yolları; sağlayıcıdan gelen URL'ye körlemesine istek atılmaz. */
export async function loadGameResource(resource: GameResource, signal: AbortSignal): Promise<unknown> {
  const options: RequestInit = { signal, cache: "no-store" };
  if (resource.kind === "session") {
    try {
      const session = await apiRequest<GameSession>("/api/v1/auth/me", options);
      if (!isGameId(session.accountId) || !Array.isArray(session.roles) || !session.roles.every((role) => typeof role === "string")) throw new Error("INVALID_SESSION_RESPONSE");
      return session;
    }
    catch (error) { if (error instanceof ApiHttpError && error.status === 401) return null; throw error; }
  }
  if (resource.kind === "managed") return apiRequest("/api/v1/game/developer/games", options);
  if (resource.kind === "prices") {
    if (!resource.gameIds.length || resource.gameIds.length > 50 || !resource.gameIds.every(isGameId)) throw new Error("INVALID_GAME_ID");
    const query = new URLSearchParams({ gameIds: [...new Set(resource.gameIds)].join(","), country: resource.country, currency: resource.currency });
    return apiRequest(`/api/v1/game/games/prices?${query}`, options);
  }
  if (resource.kind === "discovery") {
    return apiRequest<DiscoveryPage>(`/api/v1/game/games?${resource.query}`, options);
  }
  if (resource.kind === "popular") return apiRequest<DiscoveryPage>(`/api/v1/game/games/popular?page=${resource.page}&size=${resource.size ?? 20}`, options);
  if (resource.kind === "catalogStatus") return apiRequest<CatalogStatus>("/api/v1/game/catalog/status", options);
  if (resource.kind === "cards") {
    const ids = [...new Set(resource.gameIds)];
    if (!ids.length || ids.length > 50 || !ids.every(isGameId)) throw new Error("INVALID_GAME_ID");
    // Herkese açık toplu kart: sayfa başına tek istek, oyun başına çağrı yok.
    return apiRequest("/api/v1/game/games/batch", { ...options, method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ gameIds: ids }) });
  }
  if (resource.kind === "feedbackItem") {
    if (!isGameId(resource.id)) throw new Error("INVALID_TARGET_ID");
    if (resource.developer) {
      if (!resource.gameId || !isGameId(resource.gameId)) throw new Error("INVALID_GAME_ID");
      return apiRequest(`/api/v1/game/developer/games/${resource.gameId}/feedback/${resource.id}`, options);
    }
    return apiRequest(`/api/v1/game/feedback/${resource.id}`, options);
  }
  if (resource.kind === "myFeedback") {
    const query = new URLSearchParams({ page: String(resource.page), size: "20" });
    if (resource.gameId) { if (!isGameId(resource.gameId)) throw new Error("INVALID_GAME_ID"); query.set("gameId", resource.gameId); }
    if (resource.status) query.set("status", resource.status);
    if (resource.type) query.set("type", resource.type);
    return apiRequest(`/api/v1/game/feedback?${query}`, options);
  }
  if (!isGameId(resource.gameId)) throw new Error("INVALID_GAME_ID");
  const gamePath = `/api/v1/game/games/${resource.gameId}` as const;
  if (resource.kind === "admin") return apiRequest(`/api/v1/game/admin/games/${resource.gameId}`, options);
  if (resource.kind === "adminAchievements") return apiRequest(`/api/v1/game/admin/games/${resource.gameId}/achievements?page=${resource.page}&size=20`, options);
  if (resource.kind === "grants") return apiRequest(`/api/v1/game/admin/games/${resource.gameId}/developers`, options);
  if (resource.kind === "developer") return apiRequest(`/api/v1/game/developer/games/${resource.gameId}/content`, options);
  if (resource.kind === "publicDeveloper") return apiRequest(`${gamePath}/developer-content`, options);
  if (resource.kind === "feedback") {
    const query = new URLSearchParams({ page: String(resource.page), size: "20" });
    if (resource.status) query.set("status", resource.status);
    if (resource.type) query.set("type", resource.type);
    if (!resource.developer) query.set("gameId", resource.gameId);
    const path: `/api/v1/game/${string}` = resource.developer ? `/api/v1/game/developer/games/${resource.gameId}/feedback` : "/api/v1/game/feedback";
    return apiRequest(`${path}?${query}`, options);
  }
  if (resource.kind === "detail") return apiRequest<GameDetail>(gamePath, options);
  if (resource.kind === "content") {
    if (!isGameId(resource.contentId)) throw new Error("INVALID_CONTENT_ID");
    return apiRequest<CommunityDetail>(`/api/v1/community/games/${resource.gameId}/content/${resource.contentId}`, options);
  }
  const page = resource.page;
  const size = 20;
  const descriptor = await apiRequest<GameModule>(`${gamePath}/modules/${resource.type}?contractVersion=2&page=${page}&size=${size}`, options);
  if (resource.type === "ACHIEVEMENTS") {
    if (descriptor.state !== "AVAILABLE" && descriptor.state !== "EMPTY") throw new Error("MODULE_UNAVAILABLE");
    return { kind: "achievements", items: descriptor.items, totalElements: descriptor.totalElements ?? 0, page, size };
  }
  const listPath = `/api/v1/community/games/${resource.gameId}/content?type=${resource.type}&page=${page}&size=${size}` as const;
  if (descriptor.state !== "EXTERNAL" || descriptor.contentSource?.service !== "COMMUNITY" || descriptor.contentSource.listPath !== listPath) {
    throw new Error("MODULE_SOURCE_UNAVAILABLE");
  }
  // Gateway çerezi apiRequest tarafından aktarılır. Servis token'ı veya paylaşımlı içerik cache'i yoktur.
  const content = await apiRequest<CommunityPage>(listPath, options);
  return { ...content, kind: "community" };
}

function mutationRoute(action: GameMutation): { path: `/api/v1/game/${string}`; method: string } {
  if (action.operation === "create") return { path: "/api/v1/game/admin/games", method: "POST" };
  if (action.operation === "popular") return { path: "/api/v1/game/admin/popular", method: "PUT" };
  if (action.operation === "feedback") return { path: "/api/v1/game/feedback", method: "POST" };
  if (!action.gameId || !isGameId(action.gameId)) throw new Error("INVALID_GAME_ID");
  const admin = `/api/v1/game/admin/games/${action.gameId}` as const;
  const developer = `/api/v1/game/developer/games/${action.gameId}` as const;
  if (action.operation === "update") return { path: admin, method: "PATCH" };
  if (action.operation === "archive" || action.operation === "restore") return { path: `${admin}/${action.operation}`, method: "POST" };
  if (action.operation === "grant") return { path: `${admin}/developers`, method: "POST" };
  if (action.operation === "release") {
    if (!["TITLE", "DESCRIPTION", "RELEASE_DATE", "GAME_TYPE", "DEVELOPERS", "PUBLISHERS", "LOCALIZATION"].includes(action.field ?? "")) throw new Error("INVALID_OVERRIDE_FIELD");
    return { path: `${admin}/manual-overrides/${action.field}/release`, method: "POST" };
  }
  if (action.operation === "description") return { path: `${developer}/description`, method: "PUT" };
  if (action.operation === "addMedia") return { path: `${developer}/media`, method: "POST" };
  if (action.operation === "addNote") return { path: `${developer}/update-notes`, method: "POST" };
  if (action.operation === "moderate") return { path: `/api/v1/game/moderation/games/${action.gameId}/developer-content/actions`, method: "POST" };
  if (action.operation === "achievement") {
    if (!/^[a-z0-9][a-z0-9-]{0,63}$/.test(action.targetId ?? "")) throw new Error("INVALID_ACHIEVEMENT_CODE");
    return { path: `${admin}/achievements/${action.targetId}`, method: "PUT" };
  }
  if (!action.targetId || !isGameId(action.targetId)) throw new Error("INVALID_TARGET_ID");
  if (action.operation === "revoke") return { path: `${admin}/developers/${action.targetId}/revoke`, method: "POST" };
  if (action.operation === "deleteMedia") return { path: `${developer}/media/${action.targetId}`, method: "DELETE" };
  if (action.operation === "updateNote" || action.operation === "deleteNote") return { path: `${developer}/update-notes/${action.targetId}`, method: action.operation === "updateNote" ? "PUT" : "DELETE" };
  if (action.operation === "feedbackStatus") return { path: `${developer}/feedback/${action.targetId}/status`, method: "PATCH" };
  throw new Error("INVALID_GAME_OPERATION");
}

/** Çerezli yazmalar CSRF alır; bilinmeyen sonuçta POST otomatik tekrarlanmaz. */
export async function mutateGame(action: GameMutation): Promise<unknown> {
  const route = mutationRoute(action);
  await apiRequest("/api/v1/auth/csrf", { cache: "no-store", signal: AbortSignal.timeout(15000) });
  const cookie = document.cookie.split(";").map((part) => part.trim()).find((part) => /^(?:__Host-)?XSRF-TOKEN=/.test(part));
  if (!cookie) throw new Error("CSRF_UNAVAILABLE");
  const csrf = decodeURIComponent(cookie.slice(cookie.indexOf("=") + 1));
  return apiRequest(route.path, { method: route.method, cache: "no-store", signal: AbortSignal.timeout(15000), headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": csrf }, body: action.body === undefined ? undefined : JSON.stringify(action.body) });
}
