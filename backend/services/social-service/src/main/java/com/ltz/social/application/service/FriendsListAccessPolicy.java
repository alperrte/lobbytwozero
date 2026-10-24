package com.ltz.social.application.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ltz.social.application.port.FriendsVisibility;
import com.ltz.social.application.port.FriendsVisibilityPort;
import com.ltz.social.application.port.PrivacyUnavailableException;
import com.ltz.social.domain.entity.FriendshipId;
import com.ltz.social.domain.exception.SocialErrorCode;
import com.ltz.social.domain.exception.SocialException;
import com.ltz.social.infrastructure.persistence.repository.FriendshipRepository;
import com.ltz.social.infrastructure.persistence.repository.UserBlockRepository;

import lombok.RequiredArgsConstructor;

/**
 * Bir kullanıcının arkadaş listesini kimin görebileceğine karar verir (SOC-12). Kurallar:
 * <ul>
 *   <li>Kullanıcı kendi listesini her zaman görür.</li>
 *   <li>Aralarında (herhangi bir yönde) engel varsa görülmez.</li>
 *   <li>Aksi halde user-service'teki tercih uygulanır: PUBLIC herkes, FRIENDS yalnız arkadaşlar, PRIVATE kimse.</li>
 *   <li>Tercih okunamazsa fail-closed: PRIVATE sayılır.</li>
 * </ul>
 * Reddedilen her durumda aynı hata döner; böylece engel, gizlilik tercihi ya da kullanıcının varlığı sızdırılmaz.
 */
@Component
@RequiredArgsConstructor
class FriendsListAccessPolicy {

    private final UserBlockRepository blocks;
    private final FriendshipRepository friendships;
    private final FriendsVisibilityPort visibilityPort;

    void ensureVisible(UUID viewerId, UUID ownerId) {
        if (viewerId.equals(ownerId)) {
            return;
        }
        if (blocks.existsBetween(viewerId, ownerId) || !permittedByPreference(viewerId, ownerId)) {
            throw new SocialException(SocialErrorCode.FRIENDS_LIST_NOT_VISIBLE);
        }
    }

    private boolean permittedByPreference(UUID viewerId, UUID ownerId) {
        return switch (visibilityOf(ownerId)) {
            case PUBLIC -> true;
            case FRIENDS -> friendships.existsById(FriendshipId.of(viewerId, ownerId));
            case PRIVATE -> false;
        };
    }

    private FriendsVisibility visibilityOf(UUID ownerId) {
        try {
            return visibilityPort.visibilityOf(ownerId);
        } catch (PrivacyUnavailableException exception) {
            return FriendsVisibility.PRIVATE;
        }
    }
}
