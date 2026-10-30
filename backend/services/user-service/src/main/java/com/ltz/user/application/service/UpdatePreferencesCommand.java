package com.ltz.user.application.service;

import com.ltz.user.domain.enums.Theme;

/** Tercihlerin tamamı (PUT) ve istemcinin son gördüğü sürüm. */
public record UpdatePreferencesCommand(long expectedVersion, String locale, Theme theme, boolean reducedMotion,
                                       boolean compactLayout) {
}
