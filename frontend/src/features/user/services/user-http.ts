import { ApiHttpError, apiRequest } from "@/shared/lib/api/client";
import { CSRF_HEADER, CSRF_PATH, readCsrfCookie } from "@/shared/lib/session/csrf";

/**
 * User uçları için ince katman. Oturum yenilemesini (401 → yenile → bir kez tekrarla) ortak `apiRequest` yapar; burada yalnız
 * durum değiştiren isteğe CSRF başlığı, tek seferlik CSRF yenileme, ham görsel gövdesi (`raw`) ile JSON gövdesi (`json`) ve
 * ağ hatasının `NETWORK` kodlu `ApiHttpError(0)` olması vardır. Mutasyon otomatik tekrarlanmaz. `AbortError` olduğu gibi bırakılır.
 */
export type UserPath = `/api/v1/user/${string}`;

export type UserRequestOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  json?: unknown;
  raw?: { data: Blob; type: string };
  signal?: AbortSignal;
};

export async function userRequest<T>(path: UserPath, options: UserRequestOptions = {}): Promise<T> {
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

async function send<T>(path: UserPath, options: UserRequestOptions): Promise<T> {
  const method = options.method ?? "GET";
  const headers: Record<string, string> = { Accept: "application/json" };
  let body: BodyInit | undefined;
  if (options.raw) {
    headers["Content-Type"] = options.raw.type;
    body = options.raw.data;
  } else if (options.json !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(options.json);
  }
  if (method !== "GET") {
    const token = await ensureCsrfToken();
    if (token) {
      headers[CSRF_HEADER] = token;
    }
  }
  return apiRequest<T>(path, { method, headers, body, signal: options.signal });
}

function toApiError(error: unknown): ApiHttpError {
  if (error instanceof ApiHttpError) {
    return error;
  }
  if (error instanceof DOMException && error.name === "AbortError") {
    throw error;
  }
  return new ApiHttpError(error instanceof TypeError ? 0 : 200, {
    code: error instanceof TypeError ? "NETWORK" : "UNKNOWN"
  });
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
