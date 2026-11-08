import { ApiHttpError } from "@/shared/lib/api/client";

/**
 * Sunucu hatasını kullanıcıya gösterilecek mesaj anahtarına (`Social.errors.*`) çevirir.
 * Önce social-service'in `ProblemDetail.code` değeri, yoksa HTTP durumu kullanılır (web-rules §4: 401/403/404/409/429/5xx ayrı).
 * Sunucu metni hiçbir zaman arayüze taşınmaz.
 */
export type SocialErrorKey =
  | "alreadyFriends"
  | "alreadyPending"
  | "incomingPending"
  | "userBlocked"
  | "inviteNotFound"
  | "requestSelf"
  | "notAllowed"
  | "notPending"
  | "tooManyPending"
  | "notFound"
  | "conflict"
  | "unauthenticated"
  | "forbidden"
  | "rateLimited"
  | "unavailable"
  | "csrf"
  | "timeout"
  | "network"
  | "generic";

const byCode: Readonly<Record<string, SocialErrorKey>> = {
  ALREADY_FRIENDS: "alreadyFriends",
  FRIEND_REQUEST_ALREADY_PENDING: "alreadyPending",
  INCOMING_FRIEND_REQUEST_PENDING: "incomingPending",
  USER_BLOCKED: "userBlocked",
  INVITE_CODE_NOT_FOUND: "inviteNotFound",
  CANNOT_REQUEST_SELF: "requestSelf",
  CANNOT_BLOCK_SELF: "requestSelf",
  FRIEND_REQUEST_NOT_ALLOWED: "notAllowed",
  FRIEND_REQUEST_NOT_PENDING: "notPending",
  TOO_MANY_PENDING_REQUESTS: "tooManyPending",
  FRIEND_REQUEST_NOT_FOUND: "notFound",
  FRIENDSHIP_NOT_FOUND: "notFound",
  BLOCK_NOT_FOUND: "notFound",
  CONCURRENT_MODIFICATION: "conflict",
  CSRF_TOKEN_INVALID: "csrf"
};

export function socialErrorKey(error: unknown): SocialErrorKey {
  if (!(error instanceof ApiHttpError)) {
    if (error instanceof DOMException && error.name === "TimeoutError") {
      return "timeout";
    }
    return error instanceof TypeError ? "network" : "generic";
  }
  const mapped = error.code ? byCode[error.code] : undefined;
  if (mapped) {
    return mapped;
  }
  switch (error.status) {
    case 401:
      return "unauthenticated";
    case 403:
      return "forbidden";
    case 404:
      return "notFound";
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
