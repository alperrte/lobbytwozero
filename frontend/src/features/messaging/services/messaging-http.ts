import { ApiHttpError, apiRequest, apiUrl, refreshSession } from "@/shared/lib/api/client";
import { CSRF_HEADER, CSRF_PATH, readCsrfCookie } from "@/shared/lib/session/csrf";

/** Mesajlaşma istekleri için üst süre: yanıt gelmezse istek iptal edilir, kullanıcı "tekrar dene" görür (web-rules §15). */
export const MESSAGING_REQUEST_TIMEOUT_MS = 15_000;
/** Yükleme en çok 1 MB'tır; yavaş bağlantıda da makul bir üst süre. */
export const UPLOAD_TIMEOUT_MS = 60_000;

type Method = "GET" | "POST" | "PUT" | "DELETE";

type MessagingRequestOptions = {
  method?: Method;
  body?: unknown;
  signal?: AbortSignal;
};

function withTimeout(signal: AbortSignal | undefined): AbortSignal {
  const timeout = AbortSignal.timeout(MESSAGING_REQUEST_TIMEOUT_MS);
  return signal ? AbortSignal.any([signal, timeout]) : timeout;
}

/** CSRF çerezi yoksa Auth'tan alır (çerezi Gateway yazar); yine de yoksa isteği hiç göndermez. */
async function csrfToken(signal: AbortSignal | undefined, refresh: boolean): Promise<string> {
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

function isCsrfRejection(error: unknown): boolean {
  return error instanceof ApiHttpError && error.status === 403 && error.code === "CSRF_TOKEN_INVALID";
}

/**
 * Gateway üzerinden mesajlaşma uçlarına istek. Okumalar doğrudan `apiRequest` ile gider (oturum yenileme oradadır).
 * Durum değiştiren istekler Gateway'in çift gönderimli CSRF denetimine takılır; `403 CSRF_TOKEN_INVALID` dönerse istek servise
 * ulaşmamıştır, çerez yenilenip bir kez tekrarlanır. Başka hiçbir hata otomatik tekrarlanmaz: mesaj gönderimi yeniden denemesi
 * kullanıcı eylemiyle ve aynı `clientMessageId` ile yapılır (sunucu çift mesaj üretmez).
 */
export async function messagingRequest<T>(path: `/${string}`, options: MessagingRequestOptions = {}): Promise<T> {
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
      if (!isCsrfRejection(error) || attempt > 0) {
        throw error;
      }
    }
  }
}

export type UploadHandle<T> = {
  promise: Promise<T>;
  abort: () => void;
};

type XhrResult = { status: number; body: unknown; retryAfter: string | null };

function sendXhr(
  path: string,
  file: Blob,
  token: string,
  onProgress: (fraction: number) => void,
  register: (xhr: XMLHttpRequest) => void
): Promise<XhrResult> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    register(xhr);
    xhr.open("POST", apiUrl(path as `/${string}`));
    xhr.withCredentials = true;
    xhr.timeout = UPLOAD_TIMEOUT_MS;
    xhr.responseType = "json";
    xhr.setRequestHeader("Content-Type", file.type);
    xhr.setRequestHeader(CSRF_HEADER, token);
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && event.total > 0) {
        onProgress(Math.min(1, event.loaded / event.total));
      }
    };
    xhr.onload = () => resolve({ status: xhr.status, body: xhr.response, retryAfter: xhr.getResponseHeader("Retry-After") });
    xhr.onerror = () => reject(new TypeError("network"));
    xhr.ontimeout = () => reject(new DOMException("timeout", "TimeoutError"));
    xhr.onabort = () => reject(new DOMException("aborted", "AbortError"));
    xhr.send(file);
  });
}

const SAFE_CODE = /^[A-Z0-9_]{1,80}$/;

function toError(result: XhrResult): ApiHttpError {
  const record = typeof result.body === "object" && result.body !== null ? (result.body as Record<string, unknown>) : {};
  const code = typeof record.code === "string" && SAFE_CODE.test(record.code) ? record.code : undefined;
  const seconds = result.retryAfter ? Number.parseInt(result.retryAfter, 10) : Number.NaN;
  return new ApiHttpError(result.status, {
    code,
    retryAfterSeconds: Number.isFinite(seconds) && seconds > 0 ? Math.min(seconds, 3600) : undefined
  });
}

/**
 * Ham gövdeli dosya yükleme (çok parçalı form değil; `Content-Type` dosyanın türü). `fetch` yükleme ilerlemesi vermediği için
 * XHR kullanılır (web-rules §7.2: yükleme ilerlemesi ve iptal). CSRF reddinde çerez yenilenip, 401'de oturum yenilenip istek bir
 * kez tekrarlanır; ikisinde de istek servise ulaşmamıştır.
 */
export function uploadRequest<T>(path: `/${string}`, file: Blob, onProgress: (fraction: number) => void): UploadHandle<T> {
  let current: XMLHttpRequest | null = null;
  let aborted = false;
  const register = (xhr: XMLHttpRequest) => {
    current = xhr;
  };

  const run = async (): Promise<T> => {
    let csrfRetried = false;
    let sessionRetried = false;
    for (;;) {
      if (aborted) {
        throw new DOMException("aborted", "AbortError");
      }
      const token = await csrfToken(undefined, csrfRetried);
      const result = await sendXhr(path, file, token, onProgress, register);
      if (result.status >= 200 && result.status < 300) {
        return result.body as T;
      }
      const error = toError(result);
      if (isCsrfRejection(error) && !csrfRetried) {
        csrfRetried = true;
        continue;
      }
      if (error.status === 401 && !sessionRetried && (await refreshSession())) {
        sessionRetried = true;
        continue;
      }
      throw error;
    }
  };

  return {
    promise: run(),
    abort: () => {
      aborted = true;
      (current as XMLHttpRequest | null)?.abort();
    }
  };
}
