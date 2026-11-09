package com.ltz.messaging.api.controller;

/** Gateway path standardı: {@code /api/v1/messaging/**} (StripPrefix yoktur; controller tam path'i karşılar). */
public final class MessagingApiPaths {

    public static final String BASE = "/api/v1/messaging";
    public static final String CONVERSATIONS = BASE + "/conversations";
    public static final String DIRECT_CONVERSATIONS = CONVERSATIONS + "/direct";
    public static final String GROUP_CONVERSATIONS = CONVERSATIONS + "/groups";
    public static final String CONVERSATION = CONVERSATIONS + "/{conversationId}";
    public static final String MEMBERS = CONVERSATION + "/members";
    public static final String MEMBER = MEMBERS + "/{userId}";
    public static final String MEMBER_ROLE = MEMBER + "/role";
    public static final String LEAVE = CONVERSATION + "/leave";
    public static final String MESSAGES = CONVERSATION + "/messages";
    public static final String MESSAGE = MESSAGES + "/{messageId}";
    public static final String MESSAGE_READS = MESSAGE + "/reads";
    public static final String MESSAGE_REACTION = MESSAGE + "/reaction";
    public static final String MESSAGE_REACTIONS = MESSAGE + "/reactions";
    public static final String MESSAGE_FORWARD = MESSAGE + "/forward";
    public static final String READ = CONVERSATION + "/read";
    public static final String ATTACHMENTS = CONVERSATION + "/attachments";
    public static final String ATTACHMENT = ATTACHMENTS + "/{attachmentId}";
    public static final String GIFS = BASE + "/gifs";
    public static final String GIF_SEARCH = GIFS + "/search";
    public static final String GIF_TRENDING = GIFS + "/trending";
    public static final String STREAM = BASE + "/stream";

    private MessagingApiPaths() {
    }
}
