package com.ltz.user.api.dto.response;

import com.ltz.user.application.service.PrivacySettingsView;
import com.ltz.user.domain.enums.GuestAccess;
import com.ltz.user.domain.enums.Visibility;

/** Kullanıcının kendi gizlilik ayarları. */
public record PrivacySettingsResponse(Visibility friendsList, Visibility library, Visibility hardware,
                                      Visibility performance, GuestAccess guestProfileAccess, long version) {

    public static PrivacySettingsResponse from(PrivacySettingsView view) {
        return new PrivacySettingsResponse(view.friendsList(), view.library(), view.hardware(), view.performance(),
                view.guestProfileAccess(), view.version());
    }
}
