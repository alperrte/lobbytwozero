package com.ltz.user.api.dto.response;

import com.ltz.user.application.service.PreferencesView;
import com.ltz.user.domain.enums.Theme;

/** Kullanıcının kendi tercihleri. */
public record PreferencesResponse(String locale, Theme theme, boolean reducedMotion, boolean compactLayout,
                                  long version) {

    public static PreferencesResponse from(PreferencesView view) {
        return new PreferencesResponse(view.locale(), view.theme(), view.reducedMotion(), view.compactLayout(),
                view.version());
    }
}
