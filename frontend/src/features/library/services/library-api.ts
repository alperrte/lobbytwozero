import { ApiHttpError, apiRequest } from "@/shared/lib/api/client";
import { CSRF_HEADER, CSRF_PATH, readCsrfCookie } from "@/shared/lib/session/csrf";
import { isLibraryId, libraryQueryString } from "../schemas/library-query";
import type { LibraryEntry, LibraryMutation, LibraryPage, LibraryPrivacy, LibraryResource, LibraryStatistics } from "../types/library";

function requireId(id: string): string {
  if (!isLibraryId(id)) throw new ApiHttpError(400);
  return id;
}
const options = (signal: AbortSignal): RequestInit => ({ signal, cache: "no-store", headers: { Accept: "application/json" } });

export const libraryApi = {
  list: (query: Parameters<typeof libraryQueryString>[0], signal: AbortSignal) => apiRequest<LibraryPage>(`/api/v1/library/entries?${libraryQueryString(query)}`, options(signal)),
  statistics: (signal: AbortSignal) => apiRequest<LibraryStatistics>("/api/v1/library/entries/statistics", options(signal)),
  profile: (userId: string, page: number, size: number, signal: AbortSignal) => {
    const params = new URLSearchParams(libraryQueryString({ page, size, sort: "ADDED_AT", direction: "DESC" }));
    return apiRequest<LibraryPage>(`/api/v1/library/users/${requireId(userId)}/entries?page=${params.get("page")}&size=${params.get("size")}`, options(signal));
  },
  profileStatistics: (userId: string, signal: AbortSignal) => apiRequest<LibraryStatistics>(`/api/v1/library/users/${requireId(userId)}/entries/statistics`, options(signal)),
  privacy: (signal: AbortSignal) => apiRequest<LibraryPrivacy>("/api/v1/user/me/privacy", options(signal))
};

export async function loadLibraryResource(resource: LibraryResource, signal: AbortSignal): Promise<unknown> {
  switch (resource.kind) {
    case "collection": return libraryApi.list(resource.query, signal);
    case "statistics": return libraryApi.statistics(signal);
    case "privacy": return libraryApi.privacy(signal);
    case "profile": {
      // İzinli liste gelmeden sayaç istenmez. İkinci izin kontrolü de başarısızsa ikisi de gösterilmez.
      const collection = await libraryApi.profile(resource.userId, resource.page, resource.size, signal);
      const statistics = await libraryApi.profileStatistics(resource.userId, signal);
      return { collection, statistics };
    }
  }
}

async function primeCsrf(signal: AbortSignal): Promise<string> {
  await apiRequest(CSRF_PATH, options(signal));
  const token = readCsrfCookie();
  if (!token) throw new ApiHttpError(403, { code: "CSRF_TOKEN_INVALID" });
  return token;
}

export async function mutateLibrary(action: LibraryMutation, signal: AbortSignal): Promise<LibraryEntry | LibraryPrivacy | undefined> {
  let path: `/${string}`;
  let method: string;
  let body: string | undefined;
  if (action.kind === "privacy") {
    path = "/api/v1/user/me/privacy"; method = "PUT";
    const { version, friendsList, hardware, performance, guestProfileAccess } = action.settings;
    body = JSON.stringify({ version, friendsList, library: action.library, hardware, performance, guestProfileAccess });
  } else {
    const id = requireId(action.gameId);
    path = action.kind === "add" ? "/api/v1/library/entries" : `/api/v1/library/entries/${id}${action.kind === "remove" ? "" : `/${action.kind}`}`;
    method = action.kind === "add" ? "POST" : action.kind === "remove" || ("enabled" in action && !action.enabled) ? "DELETE" : "PUT";
    if (action.kind === "add") body = JSON.stringify({ gameId: id });
  }
  let token = readCsrfCookie() ?? await primeCsrf(signal);
  for (let attempt = 0; ; attempt++) {
    try {
      return await apiRequest(path, { ...options(signal), method, body, headers: { Accept: "application/json", [CSRF_HEADER]: token, ...(body ? { "Content-Type": "application/json" } : {}) } });
    } catch (error) {
      if (!(error instanceof ApiHttpError) || error.status !== 403 || error.code !== "CSRF_TOKEN_INVALID" || attempt > 0) throw error;
      token = await primeCsrf(signal);
    }
  }
}
