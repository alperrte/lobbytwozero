import { ApiHttpError, apiRequest } from "@/shared/lib/api/client";
import { CSRF_HEADER, CSRF_PATH, readCsrfCookie } from "@/shared/lib/session/csrf";
import { knownAuthErrorCodes, type AuthErrorCode } from "../types/auth.types";

/**
 * Auth uçları için ince sarmalayıcı. HTTP'nin kendisi ortak `apiRequest` üzerinden gider (taban adres, çerezler,
 * `ApiHttpError` ve OTURUM YENİLEMESİ: 401 → `POST /session/refresh` → bir kez tekrar, ortak katmanda ve tüm servisler için
 * aynıdır); burada yalnız Auth'a özgü olanlar bulunur:
 *
 * - Durum değiştiren her istekte CSRF başlığı (`X-XSRF-TOKEN`, çerez yoksa önce `GET /csrf`).
 * - `CSRF_TOKEN_INVALID` gelirse çerez yenilenir ve istek BİR kez tekrarlanır.
 * - Ağ hatası `NETWORK` koduyla `ApiHttpError(0)` olur ve mutasyon otomatik tekrarlanmaz; kullanıcı iptali
 *   (`AbortError`) olduğu gibi bırakılır. Parola/token hiçbir yerde loglanmaz ve saklanmaz.
 */
export type AuthPath = `/api/v1/auth/${string}`;

export type AuthRequestOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  signal?: AbortSignal;
};

export async function authRequest<T>(
  path: AuthPath,
  options: AuthRequestOptions = {}
): Promise<T> {
  const mutation = (options.method ?? "GET") !== "GET";
  let csrfRetried = false;

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
