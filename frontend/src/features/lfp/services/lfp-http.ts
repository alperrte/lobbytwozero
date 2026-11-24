import { ApiHttpError, apiRequest } from "@/shared/lib/api/client";
import { CSRF_HEADER, CSRF_PATH, readCsrfCookie } from "@/shared/lib/session/csrf";

/** LFP istekleri için üst süre: yanıt gelmezse istek iptal edilir, kullanıcı "tekrar dene" görür (web-rules §15). */
export const LFP_REQUEST_TIMEOUT_MS = 15_000;

type Method = "GET" | "POST" | "PUT";

function withTimeout(signal: AbortSignal | undefined): AbortSignal {
  const timeout = AbortSignal.timeout(LFP_REQUEST_TIMEOUT_MS);
  return signal ? AbortSignal.any([signal, timeout]) : timeout;
}

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
 * Gateway üzerinden LFP uçlarına istek. Okumalar doğrudan `apiRequest` ile gider (oturum yenileme oradadır). Durum değiştiren
 * istekler çift gönderimli CSRF başlığı taşır; Gateway `403 CSRF_TOKEN_INVALID` dönerse istek servise ulaşmamıştır, çerez
 * yenilenip bir kez tekrarlanır. Başka hiçbir hata otomatik tekrarlanmaz (ilan oluşturma idempotent değildir).
 */
export async function lfpRequest<T>(
  path: `/${string}`,
  options: { method?: Method; body?: unknown; signal?: AbortSignal } = {}
): Promise<T> {
  const method = options.method ?? "GET";
  const signal = withTimeout(options.signal);
  if (method === "GET") {
    return apiRequest<T>(path, { method, cache: "no-store", signal });
  }
  const body = options.body === undefined ? undefined : JSON.stringify(options.body);
  const contentType: Record<string, string> = body === undefined ? {} : { "Content-Type": "application/json" };
  for (let attempt = 0; ; attempt += 1) {
    const token = await csrfToken(signal, attempt > 0);
    try {
      return await apiRequest<T>(path, { method, cache: "no-store", signal, body, headers: { ...contentType, [CSRF_HEADER]: token } });
    } catch (error) {
      const csrfRejected = error instanceof ApiHttpError && error.status === 403 && error.code === "CSRF_TOKEN_INVALID";
      if (!csrfRejected || attempt > 0) throw error;
    }
  }
}
