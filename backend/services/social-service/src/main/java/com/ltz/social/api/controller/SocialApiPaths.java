package com.ltz.social.api.controller;

/** Gateway path standardı: {@code /api/v1/social/**} (StripPrefix yoktur; controller tam path'i karşılar). */
public final class SocialApiPaths {

    public static final String BASE = "/api/v1/social";
    public static final String FRIEND_REQUESTS = BASE + "/friend-requests";
    public static final String FRIEND_REQUESTS_INCOMING = FRIEND_REQUESTS + "/incoming";
    public static final String FRIEND_REQUESTS_OUTGOING = FRIEND_REQUESTS + "/outgoing";
    public static final String FRIEND_REQUEST_ACCEPT = FRIEND_REQUESTS + "/{requestId}/accept";
    public static final String FRIEND_REQUEST_REJECT = FRIEND_REQUESTS + "/{requestId}/reject";
    public static final String FRIEND_REQUEST_CANCEL = FRIEND_REQUESTS + "/{requestId}/cancel";

    private SocialApiPaths() {
    }
}
