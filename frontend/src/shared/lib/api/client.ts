const API_BASE_URL = (
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:5080"
).replace(/\/$/, "");

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

export async function apiRequest<T>(
  path: `/${string}`,
  options: RequestInit = {}
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    credentials: "include",
    ...options
  });

  if (!response.ok) {
    throw new ApiHttpError(response.status, await readErrorDetails(response));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
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
