package com.ltz.user.application.service;

import com.ltz.user.domain.entity.PrivacySettings;
import com.ltz.user.domain.enums.GuestAccess;
import com.ltz.user.domain.enums.Visibility;

/** Kullanıcının kendi gizlilik ayarları. Ayar hiç kaydedilmediyse varsayılanlar ve sürüm 0. */
public record PrivacySettingsView(Visibility friendsList, Visibility library, Visibility hardware,
                                  Visibility performance, GuestAccess guestProfileAccess, long version) {

    static PrivacySettingsView of(PrivacySettings settings) {
        return new PrivacySettingsView(settings.getFriendsList(), settings.getLibrary(), settings.getHardware(),
                settings.getPerformance(), settings.getGuestAccess(), settings.getVersion());
    }
}
