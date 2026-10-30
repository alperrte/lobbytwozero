package com.ltz.user.api.controller;

/** Gateway formatındaki yollar (/api/v1/user/**); Gateway önek kırpmaz, controller'lar tam yolu karşılar. */
public final class UserApiPaths {

    public static final String BASE = "/api/v1/user";
    public static final String ME = BASE + "/me";
    public static final String ME_PROFILE = ME + "/profile";
    public static final String ME_AVATAR = ME + "/avatar";
    public static final String ME_COVER = ME + "/cover";
    /** Herkese açık profil görselleri: {@code GET /api/v1/user/media/<dosya>}. */
    public static final String MEDIA = BASE + "/media";

    private UserApiPaths() {
    }
}
