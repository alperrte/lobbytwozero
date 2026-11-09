import { ApiHttpError, apiRequest } from "@/shared/lib/api/client";
import { CSRF_HEADER, CSRF_PATH, readCsrfCookie } from "@/shared/lib/session/csrf";

/** Sosyal istekler için üst süre: yanıt gelmezse istek iptal edilir, kullanıcı "tekrar dene" görür (web-rules §15). */
export const SOCIAL_REQUEST_TIMEOUT_MS = 15_000;

type Method = "GET" | "POST" | "DELETE";

type SocialRequestOptions = {
  method?: Method;
  body?: unknown;
  signal?: AbortSignal;
};

function withTimeout(signal: AbortSignal | undefined): AbortSignal {
  const timeout = AbortSignal.timeout(SOCIAL_REQUEST_TIMEOUT_MS);
  return signal ? AbortSignal.any([signal, timeout]) : timeout;
}

/** CSRF çerezi yoksa Auth'tan alır (çerezi Gateway yazar); yine de yoksa isteği hiç göndermez. */
async function csrfToken(signal: AbortSignal, refresh: boolean): Promise<string> {
  let token = refresh ? null : readCsrfCookie();
  if (!token) {
    await apiRequest(CSRF_PATH, { cache: "no-store", signal });
    token = readCsrfCookie();
  }
  if (!token) {
    throw new ApiHttpError(403, { code: "CSRF_TOKEN_INVALID" });
  }
  return token;
}

/**
 * Gateway üzerinden sosyal (ve user) uçlarına istek. Okumalar doğrudan `apiRequest` ile gider (oturum yenileme oradadır).
 *
 * Durum değiştiren istekler (POST/DELETE) Gateway'in çift gönderimli CSRF denetimine takılır: çerezdeki değer
 * `X-XSRF-TOKEN` başlığında da gönderilir. Gateway `403 CSRF_TOKEN_INVALID` dönerse istek servise HİÇ ULAŞMAMIŞTIR; bu
 * yüzden çerez yenilenip istek bir kez tekrarlanır. Başka hiçbir hata otomatik tekrarlanmaz (POST idempotent değildir).
 */
export async function socialRequest<T>(path: `/${string}`, options: SocialRequestOptions = {}): Promise<T> {
  const method = options.method ?? "GET";
  const signal = withTimeout(options.signal);
  const body = options.body === undefined ? undefined : JSON.stringify(options.body);
  const contentType: Record<string, string> = body === undefined ? {} : { "Content-Type": "application/json" };

  if (method === "GET") {
    return apiRequest<T>(path, { method, cache: "no-store", signal });
  }

  for (let attempt = 0; ; attempt += 1) {
    const token = await csrfToken(signal, attempt > 0);
    try {
      return await apiRequest<T>(path, {
        method,
        cache: "no-store",
        signal,
        body,
        headers: { ...contentType, [CSRF_HEADER]: token }
      });
    } catch (error) {
      const csrfRejected = error instanceof ApiHttpError && error.status === 403 && error.code === "CSRF_TOKEN_INVALID";
      if (!csrfRejected || attempt > 0) {
        throw error;
      }
    }
  }
}
