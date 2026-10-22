package com.ltz.social.application.port;

/** Kullanıcının arkadaş listesi görünürlük tercihi (user-service {@code privacy_settings.friends_visibility}). */
public enum FriendsVisibility {
    /** Giriş yapmış herkes görür. */
    PUBLIC,
    /** Yalnız arkadaşları görür. */
    FRIENDS,
    /** Yalnız kullanıcının kendisi görür. */
    PRIVATE
}
