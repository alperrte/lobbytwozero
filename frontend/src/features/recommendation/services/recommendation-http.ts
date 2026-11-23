import { ApiHttpError, apiRequest } from "@/shared/lib/api/client";
import { CSRF_HEADER, CSRF_PATH, readCsrfCookie } from "@/shared/lib/session/csrf";

/**
 * Recommendation ve zenginleştirme isteklerinin ortak HTTP katmanı (`hardware-api.ts` deseni):
 * - Okuma: 20 sn zaman aşımı, `cache: "no-store"` (yanıtlar `private, no-store`; tarayıcı önbelleğine kişisel veri yazılmaz).
 * - Yazma: CSRF çerezi yoksa önce `GET /auth/csrf`; Gateway `CSRF_TOKEN_INVALID` (403) dönerse belirteç yenilenir ve istek
 *   YALNIZ BİR KEZ tekrarlanır. Ağ hatası `NETWORK` kodlu `ApiHttpError(0)` olur; iptal (Abort) olduğu gibi iletilir.
 */
const TIMEOUT_MS = 20000;

function withTimeout(signal?: AbortSignal): AbortSignal {
  const timeout = AbortSignal.timeout(TIMEOUT_MS);
  return signal ? AbortSignal.any([signal, timeout]) : timeout;
}

function isAbort(error: unknown, signal?: AbortSignal): boolean {
  return signal?.aborted === true || (error instanceof DOMException && error.name === "AbortError");
}

function normalize(error: unknown, signal?: AbortSignal): unknown {
  if (error instanceof ApiHttpError || isAbort(error, signal)) return error;
  return new ApiHttpError(0, { code: "NETWORK" });
}

export async function readJson(path: `/${string}`, signal?: AbortSignal, init: RequestInit = {}): Promise<unknown> {
  try {
    return await apiRequest<unknown>(path, { ...init, headers: { Accept: "application/json", ...init.headers }, signal: withTimeout(signal), cache: "no-store" });
  } catch (error) {
    throw normalize(error, signal);
  }
}

async function primeCsrf(): Promise<void> {
  try {
    await apiRequest<void>(CSRF_PATH, { headers: { Accept: "application/json" }, cache: "no-store", signal: AbortSignal.timeout(10000) });
  } catch {
    // Yazma isteği reddedilirse hata orada görünür.
  }
}

export async function writeJson(method: "POST" | "PUT", path: `/${string}`, body: unknown, signal?: AbortSignal): Promise<unknown> {
  let retried = false;
  for (;;) {
    if (!readCsrfCookie()) await primeCsrf();
    const token = readCsrfCookie();
    try {
      return await apiRequest<unknown>(path, {
        method,
        cache: "no-store",
        signal: withTimeout(signal),
        headers: { Accept: "application/json", "Content-Type": "application/json", ...(token ? { [CSRF_HEADER]: token } : {}) },
        body: JSON.stringify(body)
      });
    } catch (error) {
      if (!retried && error instanceof ApiHttpError && error.status === 403 && error.code === "CSRF_TOKEN_INVALID") {
        retried = true;
        await primeCsrf();
        continue;
      }
      throw normalize(error, signal);
    }
  }
}

export function queryString(values: Record<string, string | number | undefined>): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) if (value !== undefined && value !== "") query.set(key, String(value));
  return query.size ? `?${query}` : "";
}
