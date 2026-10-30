package com.ltz.user.api.controller;

/** Gateway formatındaki yollar (/api/v1/user/**); Gateway önek kırpmaz, controller'lar tam yolu karşılar. */
public final class UserApiPaths {

    public static final String BASE = "/api/v1/user";
    public static final String ME = BASE + "/me";
    public static final String ME_PROFILE = ME + "/profile";
    public static final String ME_AVATAR = ME + "/avatar";
    public static final String ME_COVER = ME + "/cover";
    public static final String ME_PRIVACY = ME + "/privacy";
    /** Herkese açık profil görselleri: {@code GET /api/v1/user/media/<dosya>}. */
    public static final String MEDIA = BASE + "/media";
    public static final String PROFILES = BASE + "/profiles";
    public static final String PROFILE = PROFILES + "/{userId}";
    public static final String PROFILE_BY_USERNAME = PROFILES + "/by-username/{username}";
    public static final String PROFILES_BATCH = PROFILES + "/batch";
    public static final String SEARCH = BASE + "/search";

    private UserApiPaths() {
    }
}
