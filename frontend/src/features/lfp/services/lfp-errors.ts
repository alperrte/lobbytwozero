import { ApiHttpError } from "@/shared/lib/api/client";

/**
 * Sunucu hatasını kullanıcıya gösterilecek mesaj anahtarına (`Lfp.errors.*`) çevirir. Önce `ProblemDetail.code`, yoksa HTTP
 * durumu kullanılır. Sunucu metni arayüze taşınmaz.
 */
export type LfpErrorKey =
  | "listingNotFound"
  | "notOwner"
  | "notOpen"
  | "conflict"
  | "openLimit"
  | "rateLimited"
  | "gameNotFound"
  | "gameNotPlayable"
  | "catalogUnavailable"
  | "blockCheckUnavailable"
  | "cannotContactSelf"
  | "invalidFilter"
  | "invalidPlayTime"
  | "playTimeAfterExpiry"
  | "invalidField"
  | "messagingNotAllowed"
  | "unauthenticated"
  | "forbidden"
  | "unavailable"
  | "csrf"
  | "timeout"
  | "network"
  | "generic";

const byCode: Readonly<Record<string, LfpErrorKey>> = {
  LISTING_NOT_FOUND: "listingNotFound",
  NOT_LISTING_OWNER: "notOwner",
  LISTING_NOT_OPEN: "notOpen",
  CONCURRENT_MODIFICATION: "conflict",
  OPEN_LISTING_LIMIT: "openLimit",
  LISTING_RATE_LIMITED: "rateLimited",
  GAME_NOT_FOUND: "gameNotFound",
  GAME_NOT_PLAYABLE: "gameNotPlayable",
  GAME_CATALOG_UNAVAILABLE: "catalogUnavailable",
  BLOCK_CHECK_UNAVAILABLE: "blockCheckUnavailable",
  CANNOT_CONTACT_SELF: "cannotContactSelf",
  INVALID_FILTER: "invalidFilter",
  INVALID_PLAY_TIME: "invalidPlayTime",
  PLAY_TIME_AFTER_EXPIRY: "playTimeAfterExpiry",
  INVALID_DESCRIPTION: "invalidField",
  INVALID_SKILL_LEVEL: "invalidField",
  INVALID_ROLES: "invalidField",
  INVALID_DURATION: "invalidField",
  VALIDATION_FAILED: "invalidField",
  MESSAGING_NOT_ALLOWED: "messagingNotAllowed",
  CSRF_TOKEN_INVALID: "csrf"
};

export function lfpErrorKey(error: unknown): LfpErrorKey {
  if (!(error instanceof ApiHttpError)) {
    if (error instanceof DOMException && error.name === "TimeoutError") return "timeout";
    return error instanceof TypeError ? "network" : "generic";
  }
  const mapped = error.code ? byCode[error.code] : undefined;
  if (mapped) return mapped;
  switch (error.status) {
    case 401:
      return "unauthenticated";
    case 403:
      return "forbidden";
    case 404:
      return "listingNotFound";
    case 409:
      return "conflict";
    case 429:
      return "rateLimited";
    case 502:
    case 503:
    case 504:
      return "unavailable";
    default:
      return "generic";
  }
}

/** Sunucunun alan hatalarını (`errors[].field`) form alanı adlarına çevirir. */
export function fieldErrorsOf(error: unknown): string[] {
  if (!(error instanceof ApiHttpError)) return [];
  const fields = new Set(error.fieldErrors.map((item) => item.field.split(/[.[]/)[0]));
  const fromCode: Record<string, string> = {
    INVALID_DESCRIPTION: "description",
    INVALID_SKILL_LEVEL: "skillLevel",
    INVALID_ROLES: "roles",
    INVALID_DURATION: "durationHours",
    INVALID_PLAY_TIME: "playTimeStart",
    PLAY_TIME_AFTER_EXPIRY: "playTimeStart"
  };
  if (error.code && fromCode[error.code]) fields.add(fromCode[error.code]);
  return [...fields];
}
