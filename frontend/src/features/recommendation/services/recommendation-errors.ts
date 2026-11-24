import { ApiHttpError } from "@/shared/lib/api/client";

/** Rıza kapalıyken backend her öneri ucunda 403 `CONSENT_REQUIRED` döner (ve kullanıcının öneri geçmişini siler). */
export const CONSENT_REQUIRED = "CONSENT_REQUIRED";

export function errorStatus(error: unknown): number {
  return error instanceof ApiHttpError ? error.status : 0;
}

export function errorCode(error: unknown): string | undefined {
  return error instanceof ApiHttpError ? error.code : undefined;
}

export function isConsentRequired(error: unknown): boolean {
  return error instanceof ApiHttpError && error.status === 403 && error.code === CONSENT_REQUIRED;
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}

/** 429/503 yanıtındaki `Retry-After` (saniye). Yoksa `undefined`; arayüz sayaç göstermez. */
export function retryAfter(error: unknown): number | undefined {
  if (!(error instanceof ApiHttpError) || (error.status !== 429 && error.status !== 503)) return undefined;
  const seconds = error.retryAfterSeconds;
  return typeof seconds === "number" && Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds) : undefined;
}
