import { ApiHttpError, apiRequest } from "@/shared/lib/api/client";
import { knownAuthErrorCodes, type AuthErrorCode } from "../types/auth.types";

/**
 * Auth uçları için ince sarmalayıcı. HTTP'nin kendisi ortak `apiRequest` üzerinden gider (taban adres, çerezler,
 * `ApiHttpError`); burada yalnız Auth'a özgü oturum politikası bulunur:
 *
 * - Durum değiştiren her istekte CSRF başlığı (`X-XSRF-TOKEN`, çerez yoksa önce `GET /csrf`).
 * - Korumalı uçta 401 gelirse `POST /session/refresh` ve isteğin BİR kez tekrarı; eşzamanlı 401'lerde tek yenileme.
 * - `CSRF_TOKEN_INVALID` gelirse çerez yenilenir ve istek BİR kez tekrarlanır.
 * - Ağ hatası `NETWORK` koduyla `ApiHttpError(0)` olur ve mutasyon otomatik tekrarlanmaz; kullanıcı iptali
 *   (`AbortError`) olduğu gibi bırakılır. Parola/token hiçbir yerde loglanmaz ve saklanmaz.
 */
export type AuthPath = `/api/v1/auth/${string}` | SessionBoundPath;

/** Auth dışı ama aynı oturum politikasını (401 → yenile → tekrar) isteyen uçlar: yalnız profil özeti. */
export type SessionBoundPath = "/api/v1/user/me";

const CSRF_PATH = "/api/v1/auth/csrf";
const REFRESH_PATH = "/api/v1/auth/session/refresh";
/** Güvenli bağlamda `__Host-` önekli, yerel http'de öneksiz; ikisi de okunabilir CSRF çerezidir. */
const CSRF_COOKIE_NAMES = ["__Host-XSRF-TOKEN", "XSRF-TOKEN"] as const;
const CSRF_HEADER = "X-XSRF-TOKEN";

/** Bu uçlarda 401 "oturum süresi doldu" değil "kimlik doğrulanamadı" demektir: yenileme denenmez. */
const NO_REFRESH_PATHS: ReadonlySet<string> = new Set([
  CSRF_PATH,
  REFRESH_PATH,
  "/api/v1/auth/login",
  "/api/v1/auth/register",
  "/api/v1/auth/email-verification/confirm",
  "/api/v1/auth/email-verification/resend",
  "/api/v1/auth/session/logout",
  "/api/v1/auth/account/deletion/cancel"
]);

const REFRESH_CONFLICT_WAIT_MS = 250;

export type AuthRequestOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  signal?: AbortSignal;
};

let refreshInFlight: Promise<boolean> | null = null;

export async function authRequest<T>(
  path: AuthPath,
  options: AuthRequestOptions = {}
): Promise<T> {
  const mutation = (options.method ?? "GET") !== "GET";
  let csrfRetried = false;
  let refreshed = false;

  for (;;) {
    try {
      return await send<T>(path, options);
    } catch (error) {
      const failure = toApiError(error);

      if (failure.code === "CSRF_TOKEN_INVALID" && mutation && !csrfRetried) {
        csrfRetried = true;
        await fetchCsrf();
        continue;
      }

      if (failure.status === 401 && !refreshed && !NO_REFRESH_PATHS.has(path)) {
        refreshed = true;
        if (await refreshSession()) {
          continue;
        }
      }

      throw failure;
    }
  }
}

/** Sunucu kodunu Auth hata koduna çevirir; bilinmeyen kod ve ApiHttpError olmayan hata `UNKNOWN` olur. */
export function authErrorCode(error: unknown): AuthErrorCode {
  if (error instanceof ApiHttpError && error.code !== undefined) {
    return (knownAuthErrorCodes as readonly string[]).includes(error.code)
      ? (error.code as AuthErrorCode)
      : "UNKNOWN";
  }
  return "UNKNOWN";
}

async function send<T>(path: AuthPath, options: AuthRequestOptions): Promise<T> {
  const method = options.method ?? "GET";
  const headers: Record<string, string> = { Accept: "application/json" };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (method !== "GET") {
    const token = await ensureCsrfToken();
    if (token) {
      headers[CSRF_HEADER] = token;
    }
  }
  return apiRequest<T>(path, {
    method,
    headers,
    signal: options.signal,
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  });
}

/** ApiHttpError'ı olduğu gibi verir; ağ hatasını `NETWORK`, bozuk gövdeyi `UNKNOWN` yapar; iptali yeniden fırlatır. */
function toApiError(error: unknown): ApiHttpError {
  if (error instanceof ApiHttpError) {
    return error;
  }
  if (error instanceof DOMException && error.name === "AbortError") {
    throw error;
  }
  return error instanceof TypeError
    ? new ApiHttpError(0, { code: "NETWORK" })
    : new ApiHttpError(200, { code: "UNKNOWN" });
}

function readCsrfCookie(): string | null {
  if (typeof document === "undefined") {
    return null;
  }
  for (const name of CSRF_COOKIE_NAMES) {
    for (const part of document.cookie.split(";")) {
      const [key, ...rest] = part.trim().split("=");
      if (key === name && rest.length > 0) {
        return decodeURIComponent(rest.join("="));
      }
    }
  }
  return null;
}

async function ensureCsrfToken(): Promise<string | null> {
  const existing = readCsrfCookie();
  if (existing) {
    return existing;
  }
  await fetchCsrf();
  return readCsrfCookie();
}

async function fetchCsrf(): Promise<void> {
  try {
    await apiRequest<void>(CSRF_PATH, { headers: { Accept: "application/json" } });
  } catch (error) {
    throw toApiError(error);
  }
}

/** Tek uçuşlu yenileme: eşzamanlı 401'ler aynı sözü bekler (aksi halde `REFRESH_CONFLICT` doğar). */
function refreshSession(): Promise<boolean> {
  refreshInFlight ??= doRefresh().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

async function doRefresh(): Promise<boolean> {
  try {
    await authRequest<unknown>(REFRESH_PATH, { method: "POST" });
    return true;
  } catch (error) {
    if (error instanceof ApiHttpError && error.code === "REFRESH_CONFLICT") {
      // Başka bir yenileme (ör. başka sekme) yürüyor; çerezlerin yazılmasına izin ver, isteği bir kez tekrarla.
      await new Promise((resolve) => setTimeout(resolve, REFRESH_CONFLICT_WAIT_MS));
      return true;
    }
    return false;
  }
}
