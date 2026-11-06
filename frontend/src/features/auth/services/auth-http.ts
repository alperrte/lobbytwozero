import {
  knownAuthErrorCodes,
  type AuthErrorCode,
  type FieldProblem
} from "../types/auth.types";

/**
 * Auth uçları için HTTP katmanı (`apiRequest`'in auth'a özel, CSRF ve oturum yenilemeli karşılığı).
 *
 * - Durum değiştiren her istekte CSRF başlığı (`X-XSRF-TOKEN`, çerez yoksa önce `GET /csrf`).
 * - Korumalı uçta 401 gelirse `POST /session/refresh` ve isteğin BİR kez tekrarı; eşzamanlı 401'lerde tek yenileme.
 * - `CSRF_TOKEN_INVALID` gelirse çerez yenilenir ve istek BİR kez tekrarlanır.
 * - Hata gövdesinden yalnız `code`, `errors[]` ve `Retry-After` okunur; sunucu metni ya da gönderilen değerler
 *   hataya taşınmaz. Parola/token hiçbir yerde loglanmaz ve saklanmaz.
 * - Ağ hatasında mutasyon otomatik tekrarlanmaz.
 */
const API_BASE_URL = (
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:5080"
).replace(/\/$/, "");

export type AuthPath = `/api/v1/auth/${string}`;

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
  "/api/v1/auth/session/logout"
]);

const REFRESH_CONFLICT_WAIT_MS = 250;
const MAX_RETRY_AFTER_SECONDS = 3600;

export class AuthApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: AuthErrorCode,
    public readonly fieldErrors: readonly FieldProblem[] = [],
    public readonly retryAfterSeconds?: number
  ) {
    super(`Auth isteği başarısız: ${status} ${code}`);
    this.name = "AuthApiError";
  }
}

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
    const response = await rawFetch(path, options);
    if (response.ok) {
      return parseSuccess<T>(response);
    }

    const error = await toError(response);

    if (error.code === "CSRF_TOKEN_INVALID" && mutation && !csrfRetried) {
      csrfRetried = true;
      await fetchCsrf();
      continue;
    }

    if (response.status === 401 && !refreshed && !NO_REFRESH_PATHS.has(path)) {
      refreshed = true;
      if (await refreshSession()) {
        continue;
      }
    }

    throw error;
  }
}

async function rawFetch(path: AuthPath, options: AuthRequestOptions): Promise<Response> {
  const method = options.method ?? "GET";
  const headers = new Headers({ Accept: "application/json" });
  if (options.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  if (method !== "GET") {
    const token = await ensureCsrfToken();
    if (token) {
      headers.set(CSRF_HEADER, token);
    }
  }
  return safeFetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    credentials: "include",
    signal: options.signal,
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  });
}

/** Ağ hatasını `NETWORK`'e çevirir; kullanıcı iptalini (AbortError) olduğu gibi bırakır. */
async function safeFetch(url: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(url, init);
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw error;
    }
    throw new AuthApiError(0, "NETWORK");
  }
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
  const response = await safeFetch(`${API_BASE_URL}${CSRF_PATH}`, {
    method: "GET",
    credentials: "include",
    headers: { Accept: "application/json" }
  });
  if (!response.ok) {
    throw await toError(response);
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
    if (error instanceof AuthApiError && error.code === "REFRESH_CONFLICT") {
      // Başka bir yenileme (ör. başka sekme) yürüyor; çerezlerin yazılmasına izin ver, isteği bir kez tekrarla.
      await new Promise((resolve) => setTimeout(resolve, REFRESH_CONFLICT_WAIT_MS));
      return true;
    }
    return false;
  }
}

async function parseSuccess<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T;
  }
  try {
    return (await response.json()) as T;
  } catch {
    throw new AuthApiError(response.status, "UNKNOWN");
  }
}

async function toError(response: Response): Promise<AuthApiError> {
  let body: unknown = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }
  const record =
    typeof body === "object" && body !== null ? (body as Record<string, unknown>) : {};
  return new AuthApiError(
    response.status,
    toErrorCode(record.code),
    toFieldProblems(record.errors),
    toRetryAfter(response.headers.get("Retry-After"))
  );
}

function toErrorCode(value: unknown): AuthErrorCode {
  return typeof value === "string" && (knownAuthErrorCodes as readonly string[]).includes(value)
    ? (value as AuthErrorCode)
    : "UNKNOWN";
}

function toFieldProblems(value: unknown): FieldProblem[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.flatMap((item) => {
    if (typeof item !== "object" || item === null) {
      return [];
    }
    const { field, code } = item as Record<string, unknown>;
    return typeof field === "string" && typeof code === "string" ? [{ field, code }] : [];
  });
}

function toRetryAfter(header: string | null): number | undefined {
  if (header === null) {
    return undefined;
  }
  const seconds = Number.parseInt(header, 10);
  return Number.isFinite(seconds) && seconds > 0
    ? Math.min(seconds, MAX_RETRY_AFTER_SECONDS)
    : undefined;
}
