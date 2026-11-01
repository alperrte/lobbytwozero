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

    private MessagingApiPaths() {
    }
}
