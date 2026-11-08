import { ApiHttpError, apiRequest } from "@/shared/lib/api/client";
import { CSRF_HEADER, CSRF_PATH, readCsrfCookie } from "@/shared/lib/session/csrf";

/**
 * Community isteklerinin tek geçidi. Okumalar doğrudan ortak istemciden geçer (çerez + otomatik oturum yenileme);
 * yazmalar önce CSRF çerezini hazırlar ve aynı değeri başlıkta gönderir. Token hiçbir depoya yazılmaz.
 * Yazma istekleri bilinmeyen sonuçta (ağ kopması) otomatik tekrarlanmaz; yalnız CSRF reddinde bir kez yeniden denenir.
 */
export type CommunityPath = `/api/v1/community/${string}`;
/** Oturumlu POST (ör. User toplu profil) de Gateway CSRF kuralına tabidir; aynı geçitten gider. */
export type GatewayPath = `/api/v1/${string}`;

export function communityGet<T>(path: CommunityPath, signal?: AbortSignal): Promise<T> {
  return apiRequest<T>(path, { signal, cache: "no-store", headers: { Accept: "application/json" } });
}

async function csrfToken(forceRefresh: boolean): Promise<string> {
  const existing = forceRefresh ? null : readCsrfCookie();
  if (existing) {
    return existing;
  }
  await apiRequest(CSRF_PATH, { cache: "no-store", headers: { Accept: "application/json" } });
  const token = readCsrfCookie();
  if (!token) {
    throw new Error("CSRF_UNAVAILABLE");
  }
  return token;
}

async function write<T>(path: GatewayPath, method: string, body: BodyInit | undefined, json: boolean, signal?: AbortSignal): Promise<T> {
  let forceRefresh = false;
  for (;;) {
    const headers: Record<string, string> = { Accept: "application/json", [CSRF_HEADER]: await csrfToken(forceRefresh) };
    if (json) {
      headers["Content-Type"] = "application/json";
    }
    try {
      return await apiRequest<T>(path, { method, body, headers, signal, cache: "no-store" });
    } catch (error) {
      if (!forceRefresh && error instanceof ApiHttpError && error.status === 403 && error.code === "CSRF_TOKEN_INVALID") {
        forceRefresh = true;
        continue;
      }
      throw error;
    }
  }
}

export function communitySend<T>(path: GatewayPath, method: "POST" | "PUT" | "PATCH" | "DELETE", payload?: unknown, signal?: AbortSignal): Promise<T> {
  return write<T>(path, method, payload === undefined ? undefined : JSON.stringify(payload), payload !== undefined, signal);
}

/** Görsel yükleme: `multipart/form-data` (tek parça `file`); Content-Type tarayıcıya bırakılır. */
export function communityUpload<T>(path: CommunityPath, file: File, signal?: AbortSignal): Promise<T> {
  const form = new FormData();
  form.set("file", file);
  return write<T>(path, "POST", form, false, signal);
}
