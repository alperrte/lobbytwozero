package com.ltz.social.infrastructure.client;

import java.util.UUID;

import com.ltz.social.application.port.FriendsVisibility;
import com.ltz.social.application.port.FriendsVisibilityPort;
import com.ltz.social.application.port.PrivacyUnavailableException;

/**
 * Varsayılan uygulama: gizlilik tercihi okunamaz. user-service henüz bir görünürlük ucu sunmuyor ve servisler
 * arası kimlik (D4) hazır değil; kimliksiz iç çağrı yapılmaz. Bu yüzden tercih bilinmez ve çağıran fail-closed
 * davranır (başkasının arkadaş listesi gösterilmez). Gerçek adaptör eklenince bu bean devre dışı kalır.
 */
public class UnavailableFriendsVisibility implements FriendsVisibilityPort {

    @Override
    public FriendsVisibility visibilityOf(UUID ownerId) {
        throw new PrivacyUnavailableException("No privacy adapter is available");
    }
}
