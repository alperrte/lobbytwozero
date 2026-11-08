import { CSRF_HEADER, CSRF_PATH, readCsrfCookie } from "../session/csrf";
import { hasSessionHint } from "../session/session-hint";

const API_BASE_URL = (
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:5080"
).replace(/\/$/, "");

const REFRESH_PATH = "/api/v1/auth/session/refresh";
const REFRESH_CONFLICT_WAIT_MS = 250;
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

const MAX_RETRY_AFTER_SECONDS = 3600;
/** Hata gövdesinden yalnız bu biçimlere uyan kod ve alan adları taşınır (sunucu metni ve değerler arayüze sızmaz). */
const SAFE_CODE = /^[A-Z0-9_]{1,80}$/;
const SAFE_FIELD = /^[A-Za-z0-9_.[\]]{1,100}$/;
const MAX_FIELDS = 100;

/**
 * Gateway'e giden tam adres (fetch DEĞİL, tarayıcı gezinmesi içindir: ör. `<a href>` ile OAuth başlatma). Taban adres
 * `NEXT_PUBLIC_API_BASE_URL`'dir; boşsa göreli yol döner (frontend ve `/api/v1` aynı host altındaysa).
 */
export function apiUrl(path: `/${string}`): string {
  return `${API_BASE_URL}${path}`;
}

/** Sunucunun doğrulama hatasında alan eşlemesi (Auth `ProblemDetail.errors[]`: alan + kod). */
export type ApiFieldError = { field: string; code: string };

/** Özet görünüm: `code` ve geçersiz alan adları (Game bileşenleri bunu okur). */
export type ApiProblem = { code?: string; fields?: string[] };

/** Hata gövdesinden okunan güvenli ayrıntılar. Sunucu metni (`detail`, `title`) ve gönderilen değerler taşınmaz. */
export type ApiErrorDetails = {
  /** `ProblemDetail.code` (ör. `INVALID_CREDENTIALS`); gövde yoksa ya da okunamıyorsa tanımsız. */
  code?: string;
  /** Auth biçimi: `errors[]` içinde alan + kod çiftleri. */
  fieldErrors?: readonly ApiFieldError[];
  /** Geçersiz alan adları (Game `violations[].field` ve Auth `errors[].field`, güvenli biçimdekiler). */
  fields?: readonly string[];
  /** `Retry-After` başlığı (saniye, en çok 1 saat); geçersizse tanımsız. */
  retryAfterSeconds?: number;
};

export class ApiHttpError extends Error {
  public readonly code?: string;
  public readonly fieldErrors: readonly ApiFieldError[];
  public readonly retryAfterSeconds?: number;
  /** `{ code, fields }` özeti; ne kod ne alan varsa tanımsız. */
  public readonly problem?: ApiProblem;

  constructor(
    public readonly status: number,
    details: ApiErrorDetails = {}
  ) {
    super(`API isteği başarısız: ${status}`);
    this.name = "ApiHttpError";
    this.code = details.code;
    this.fieldErrors = details.fieldErrors ?? [];
    this.retryAfterSeconds = details.retryAfterSeconds;
    const fields = details.fields ? [...details.fields] : [];
    this.problem = details.code !== undefined || fields.length > 0 ? { code: details.code, fields } : undefined;
  }
}

export type ApiRequestOptions = RequestInit & {
  /** Otomatik oturum yenilemesini (401 → yenile → bir kez tekrarla) kapatır. Yalnız özel durumlar için. */
  skipSessionRefresh?: boolean;
};

function sendRequest(path: string, init: RequestInit): Promise<Response> {
  return fetch(`${API_BASE_URL}${path}`, { credentials: "include", ...init });
}

/**
 * TÜM servislerin ortak istek fonksiyonu (taban adres, çerezler, `ApiHttpError`). OTURUM YENİLEMESİ otomatiktir: access token
 * kısa ömürlüdür (60 sn); istek 401 dönerse VE tarayıcıda oturum ipucu çerezi varsa `POST /session/refresh` çağrılır ve istek BİR
 * kez tekrarlanır (eşzamanlı 401'lerde tek yenileme). İpucu yoksa (misafir) ya da Auth'un kimlik uçlarında (login, kayıt…) yenileme
 * denenmez. Böylece her servisin sayfası, Auth'a özel bir şey yapmadan oturumu dakikalar sonra da korur. CSRF başlığını durum
 * değiştiren isteği yazan taraf ekler (Auth için `authRequest`).
 */
export async function apiRequest<T>(
  path: `/${string}`,
  options: ApiRequestOptions = {}
): Promise<T> {
  const { skipSessionRefresh = false, ...init } = options;
  let response = await sendRequest(path, init);

  if (response.status === 401 && !skipSessionRefresh && canRefreshSession(path) && (await refreshSession())) {
    response = await sendRequest(path, init);
  }

  if (!response.ok) {
    throw new ApiHttpError(response.status, await readErrorDetails(response));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

function canRefreshSession(path: string): boolean {
  return hasSessionHint() && !NO_REFRESH_PATHS.has(path.split("?")[0] ?? path);
}

let refreshInFlight: Promise<boolean> | null = null;

/** Tek uçuşlu yenileme: eşzamanlı 401'ler aynı sözü bekler (aksi halde `REFRESH_CONFLICT` doğar). Başarıda çerezler yenilenmiştir. */
export function refreshSession(): Promise<boolean> {
  refreshInFlight ??= doRefresh().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

async function errorCodeOf(response: Response): Promise<string | undefined> {
  try {
    const body: unknown = typeof response.json === "function" ? await response.json() : null;
    const code = typeof body === "object" && body !== null ? (body as Record<string, unknown>).code : undefined;
    return typeof code === "string" ? code : undefined;
  } catch {
    return undefined;
  }
}

async function primeCsrf(): Promise<string | null> {
  try {
    await sendRequest(CSRF_PATH, { headers: { Accept: "application/json" } });
  } catch {
    return null;
  }
  return readCsrfCookie();
}

async function doRefresh(): Promise<boolean> {
  try {
    let csrfRetried = false;
    for (;;) {
      const token = readCsrfCookie() ?? (await primeCsrf());
      const headers: Record<string, string> = { Accept: "application/json" };
      if (token) {
        headers[CSRF_HEADER] = token;
      }
      const response = await sendRequest(REFRESH_PATH, { method: "POST", headers });
      if (response.ok) {
        return true;
      }
      const code = await errorCodeOf(response);
      if (code === "REFRESH_CONFLICT") {
        // Başka bir yenileme (ör. başka sekme) yürüyor; çerezlerin yazılmasına izin ver, isteği bir kez tekrarla.
        await new Promise((resolve) => setTimeout(resolve, REFRESH_CONFLICT_WAIT_MS));
        return true;
      }
      if (code === "CSRF_TOKEN_INVALID" && !csrfRetried) {
        csrfRetried = true;
        await primeCsrf();
        continue;
      }
      return false;
    }
  } catch {
    return false;
  }
}

/** Hata gövdesini ve `Retry-After`'ı güvenle okur; gövde yoksa ya da JSON değilse boş ayrıntı döner. */
async function readErrorDetails(response: Response): Promise<ApiErrorDetails> {
  let body: unknown = null;
  try {
    body = typeof response.json === "function" ? await response.json() : null;
  } catch {
    body = null;
  }
  const record =
    typeof body === "object" && body !== null ? (body as Record<string, unknown>) : {};

  return {
    code: typeof record.code === "string" && SAFE_CODE.test(record.code) ? record.code : undefined,
    fieldErrors: toFieldErrors(record.errors),
    fields: toFieldNames(record.violations, record.errors),
    retryAfterSeconds: toRetryAfterSeconds(response.headers?.get?.("Retry-After") ?? null)
  };
}

function toFieldErrors(value: unknown): ApiFieldError[] {
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

/** Game `violations[]` ve Auth `errors[]` içindeki alan adlarını, yalnız güvenli biçimdekileri ve tekrarsız toplar. */
function toFieldNames(...sources: unknown[]): string[] {
  const names: string[] = [];
  for (const source of sources) {
    if (!Array.isArray(source)) {
      continue;
    }
    for (const item of source) {
      const field = typeof item === "object" && item !== null ? (item as Record<string, unknown>).field : undefined;
      if (typeof field === "string" && SAFE_FIELD.test(field) && !names.includes(field)) {
        names.push(field);
      }
    }
  }
  return names.slice(0, MAX_FIELDS);
}

function toRetryAfterSeconds(header: string | null): number | undefined {
  if (header === null) {
    return undefined;
  }
  const seconds = Number.parseInt(header, 10);
  return Number.isFinite(seconds) && seconds > 0
    ? Math.min(seconds, MAX_RETRY_AFTER_SECONDS)
    : undefined;
}
