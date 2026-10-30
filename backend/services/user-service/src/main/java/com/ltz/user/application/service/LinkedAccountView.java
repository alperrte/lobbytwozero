package com.ltz.user.application.service;

import java.time.Instant;

import com.ltz.user.domain.entity.LinkedAccount;
import com.ltz.user.domain.enums.LinkedProvider;

/**
 * Sahibin kendi gördüğü bağlantı durumu. Bağlı değilse kimlik alanları {@code null}; {@code version}, izin
 * güncellemesinde beklenen sürümdür (satır yoksa 0).
 */
public record LinkedAccountView(LinkedProvider provider, boolean linked, String externalId, String profileUrl,
                                boolean showOnProfile, Instant linkedAt, long version) {

    static LinkedAccountView unlinked(LinkedProvider provider) {
        return new LinkedAccountView(provider, false, null, null, false, null, 0);
    }

    static LinkedAccountView of(LinkedAccount account) {
        if (!account.isActive()) {
            return new LinkedAccountView(account.getProvider(), false, null, null, false, null, account.getVersion());
        }
        return new LinkedAccountView(account.getProvider(), true, account.getExternalId(),
                account.getProvider().profileUrl(account.getExternalId()), account.isShowOnProfile(),
                account.getLinkedAt(), account.getVersion());
    }
}
