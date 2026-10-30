package com.ltz.user.application.service;

import com.ltz.user.domain.entity.UserPreferences;
import com.ltz.user.domain.enums.Theme;

/** Kullanıcının kendi tercihleri. Hiç kaydedilmediyse varsayılanlar ve sürüm 0. */
public record PreferencesView(String locale, Theme theme, boolean reducedMotion, boolean compactLayout, long version) {

    static PreferencesView of(UserPreferences preferences) {
        return new PreferencesView(preferences.getLocale(), preferences.getTheme(), preferences.isReducedMotion(),
                preferences.isCompactLayout(), preferences.getVersion());
    }
}
