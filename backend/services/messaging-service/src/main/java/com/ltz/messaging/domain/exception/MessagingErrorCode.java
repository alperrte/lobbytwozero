package com.ltz.messaging.domain.exception;

/** Messaging API'nin istemciye dönen hata sözleşmesi (HTTP durumu + kod + güvenli açıklama). */
public enum MessagingErrorCode {
    CANNOT_MESSAGE_SELF(400, "A direct conversation cannot be started with yourself."),
    CANNOT_ADD_SELF(400, "You are already part of this conversation."),
    CANNOT_REMOVE_SELF(400, "Use leave to exit a group conversation."),
    CANNOT_CHANGE_OWN_ROLE(400, "You cannot change your own role."),
    ROLE_NOT_ASSIGNABLE(400, "This role cannot be assigned."),
    INVALID_CURSOR(400, "The pagination cursor is invalid."),
    INVALID_PAGE_SIZE(400, "The page size is out of range."),
    CONFLICTING_PAGE_DIRECTION(400, "Use either before or after, not both."),
    MESSAGE_CONTENT_REQUIRED(400, "A message needs a text body, an attachment or a GIF."),
    CONFLICTING_MESSAGE_CONTENT(400, "A message can carry either an attachment or a GIF, not both."),
    INVALID_GIF_QUERY(400, "The GIF search parameters are invalid."),
    INVALID_DELETE_SCOPE(400, "scope must be 'me' or 'everyone'."),
    INVALID_REACTION(400, "A reaction must be a single emoji."),
    DUPLICATE_FORWARD_TARGET(400, "Each target conversation can be used once."),
    INVALID_WAVEFORM(400, "waveform must be 64 comma-separated integers between 0 and 100, for voice uploads only."),
    VOICE_CAPTION_NOT_ALLOWED(400, "A voice message cannot have a text body."),
    MESSAGING_NOT_ALLOWED(403, "You cannot interact with this user."),
    INSUFFICIENT_GROUP_ROLE(403, "Your group role does not allow this action."),
    NOT_MESSAGE_SENDER(403, "Only the sender can see who read this message."),
    NOT_ALLOWED_TO_DELETE(403, "You cannot delete this message for everyone."),
    CONVERSATION_NOT_FOUND(404, "Conversation was not found."),
    MESSAGE_NOT_FOUND(404, "Message was not found."),
    MEMBER_NOT_FOUND(404, "This user is not a member of the conversation."),
    ATTACHMENT_NOT_FOUND(404, "Attachment was not found."),
    GROUP_CONVERSATION_REQUIRED(409, "This action is only available in group conversations."),
    ALREADY_MEMBER(409, "A user is already a member of this conversation."),
    GROUP_MEMBER_LIMIT_REACHED(409, "The group member limit has been reached."),
    CONCURRENT_MODIFICATION(409, "The resource was modified concurrently. Retry the request."),
    CLIENT_MESSAGE_ID_REUSED(409, "This clientMessageId was already used for a different message."),
    INVALID_IMAGE(422, "The file is not a supported image (PNG, JPEG or GIF) or does not match its content type."),
    IMAGE_DIMENSIONS_EXCEEDED(422, "The image dimensions or frame count are out of range."),
    IMAGE_TOO_LARGE(422, "The image is larger than 1 MB after processing."),
    ATTACHMENT_NOT_AVAILABLE(422, "The attachment does not exist, has expired or was already sent."),
    GIF_NOT_AVAILABLE(422, "The GIF was not found or is not allowed."),
    INVALID_AUDIO(422, "The file is not a supported voice recording (Opus in WebM or Ogg) or does not match its "
            + "content type."),
    AUDIO_DURATION_OUT_OF_RANGE(422, "The voice recording must be between 0.5 seconds and 2 minutes long."),
    AUDIO_TOO_LARGE(422, "The voice recording is larger than 1 MB."),
    MESSAGE_DELETED(422, "This message was deleted."),
    DELETE_WINDOW_EXPIRED(422, "This message can no longer be deleted for everyone."),
    TOO_MANY_MESSAGES(429, "Too many messages sent. Wait a moment and try again."),
    TOO_MANY_STREAMS(429, "Too many open message streams for this account."),
    TOO_MANY_UPLOADS(429, "Too many files uploaded. Wait a moment and try again."),
    TOO_MANY_PENDING_ATTACHMENTS(429, "Too many uploaded files are waiting to be sent."),
    TOO_MANY_GIF_SEARCHES(429, "Too many GIF searches. Wait a moment and try again."),
    TOO_MANY_REACTIONS(429, "Too many reactions. Wait a moment and try again."),
    BLOCK_CHECK_UNAVAILABLE(503, "Block status could not be verified. Try again later."),
    MEDIA_BUSY(503, "Media processing is busy. Try again shortly."),
    GIF_PROVIDER_UNAVAILABLE(503, "GIF search is not available right now.");

    private final int httpStatus;
    private final String detail;

    MessagingErrorCode(int httpStatus, String detail) {
        this.httpStatus = httpStatus;
        this.detail = detail;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String detail() {
        return detail;
    }
}
