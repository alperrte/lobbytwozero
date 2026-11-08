import { ApiHttpError } from "@/shared/lib/api/client";

/** `Community.errors.<key>` mesaj anahtarları. Sunucu metni arayüze taşınmaz; yalnız durum kodu eşlenir. */
export type CommunityErrorKey =
  | "network"
  | "validation"
  | "unauthorized"
  | "forbidden"
  | "notFound"
  | "conflict"
  | "tooLarge"
  | "unsupportedMedia"
  | "unprocessable"
  | "rateLimited"
  | "unavailable"
  | "server";

const BY_STATUS: Readonly<Record<number, CommunityErrorKey>> = {
  400: "validation",
  401: "unauthorized",
  403: "forbidden",
  404: "notFound",
  409: "conflict",
  413: "tooLarge",
  415: "unsupportedMedia",
  422: "unprocessable",
  429: "rateLimited",
  502: "unavailable",
  503: "unavailable",
  504: "unavailable"
};

export function errorKeyOf(error: unknown): CommunityErrorKey {
  if (error instanceof ApiHttpError) {
    return BY_STATUS[error.status] ?? (error.status >= 500 ? "server" : "validation");
  }
  return "network";
}

export function statusOf(error: unknown): number {
  return error instanceof ApiHttpError ? error.status : 0;
}

/** İstek iptali (sayfa değişti) hata değildir. */
export function isAbort(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}
