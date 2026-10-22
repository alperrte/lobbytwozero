package com.ltz.social.application.port;

import java.util.UUID;

/**
 * Bir kullanıcının arkadaş listesi görünürlük tercihini sahibi olan user-service'ten okur (SOC-12). Tercih Social
 * veritabanında tutulmaz. Tercih belirlenemezse {@link PrivacyUnavailableException} fırlatılır; çağıran bunu
 * fail-closed ({@code PRIVATE}) sayar.
 */
public interface FriendsVisibilityPort {

    FriendsVisibility visibilityOf(UUID ownerId);
}
