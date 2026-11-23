import { ApiHttpError } from "@/shared/lib/api/client";

/**
 * Sunucu hatasını kullanıcıya gösterilecek mesaj anahtarına (`Messaging.errors.*`) çevirir. Önce messaging-service'in
 * `ProblemDetail.code` değeri, yoksa HTTP durumu kullanılır. Sunucu metni hiçbir zaman arayüze taşınmaz.
 */
export type MessagingErrorKey =
  | "conversationNotFound"
  | "messageNotFound"
  | "notAllowed"
  | "blockCheckUnavailable"
  | "messageDeleted"
  | "deleteWindowExpired"
  | "notAllowedToDelete"
  | "tooManyMessages"
  | "tooManyUploads"
  | "tooManyPending"
  | "tooManyReactions"
  | "tooManyGifSearches"
  | "gifUnavailable"
  | "gifNotAvailable"
  | "attachmentNotAvailable"
  | "invalidImage"
  | "imageTooLarge"
  | "imageDimensions"
  | "unsupportedMedia"
  | "mediaBusy"
  | "payloadTooLarge"
  | "invalidReaction"
  | "insufficientRole"
  | "alreadyMember"
  | "memberLimit"
  | "memberNotFound"
  | "cannotMessageSelf"
  | "invalidContent"
  | "busy"
  | "unauthenticated"
  | "forbidden"
  | "notFound"
  | "conflict"
  | "rateLimited"
  | "unavailable"
  | "csrf"
  | "timeout"
  | "network"
  | "generic";

const byCode: Readonly<Record<string, MessagingErrorKey>> = {
  CONVERSATION_NOT_FOUND: "conversationNotFound",
  MESSAGE_NOT_FOUND: "messageNotFound",
  MESSAGING_NOT_ALLOWED: "notAllowed",
  BLOCK_CHECK_UNAVAILABLE: "blockCheckUnavailable",
  MESSAGE_DELETED: "messageDeleted",
  DELETE_WINDOW_EXPIRED: "deleteWindowExpired",
  NOT_ALLOWED_TO_DELETE: "notAllowedToDelete",
  TOO_MANY_MESSAGES: "tooManyMessages",
  TOO_MANY_UPLOADS: "tooManyUploads",
  TOO_MANY_PENDING_ATTACHMENTS: "tooManyPending",
  TOO_MANY_REACTIONS: "tooManyReactions",
  TOO_MANY_GIF_SEARCHES: "tooManyGifSearches",
  GIF_PROVIDER_UNAVAILABLE: "gifUnavailable",
  GIF_NOT_AVAILABLE: "gifNotAvailable",
  ATTACHMENT_NOT_AVAILABLE: "attachmentNotAvailable",
  INVALID_IMAGE: "invalidImage",
  IMAGE_TOO_LARGE: "imageTooLarge",
  IMAGE_DIMENSIONS_EXCEEDED: "imageDimensions",
  UNSUPPORTED_MEDIA_TYPE: "unsupportedMedia",
  MEDIA_BUSY: "mediaBusy",
  PAYLOAD_TOO_LARGE: "payloadTooLarge",
  INVALID_REACTION: "invalidReaction",
  INSUFFICIENT_GROUP_ROLE: "insufficientRole",
  ALREADY_MEMBER: "alreadyMember",
  GROUP_MEMBER_LIMIT_REACHED: "memberLimit",
  MEMBER_NOT_FOUND: "memberNotFound",
  CANNOT_MESSAGE_SELF: "cannotMessageSelf",
  VALIDATION_FAILED: "invalidContent",
  MESSAGE_CONTENT_REQUIRED: "invalidContent",
  SERVICE_BUSY: "busy",
  CSRF_TOKEN_INVALID: "csrf"
};

export function messagingErrorKey(error: unknown): MessagingErrorKey {
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
    case 413:
      return "payloadTooLarge";
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

export function isAbort(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}
