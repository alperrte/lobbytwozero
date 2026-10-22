package com.ltz.social.domain.event;

import java.util.UUID;

/**
 * Social Service'in diğer servislere yayımladığı olay sözleşmesi (docs/architecture.md §6.3). Kayıt bileşenleri
 * olayın {@code payload}'ıdır; yalnız kimlik ve enum taşır, kişisel veri (ad, e-posta, mesaj) taşımaz.
 */
public sealed interface SocialEvent permits FriendRequestSentEvent, FriendRequestAcceptedEvent,
        FriendshipChangedEvent {

    String eventType();

    int eventVersion();

    /** Olayın ait olduğu kaynak türü ({@code FriendRequest}, {@code Friendship}). */
    String aggregateType();

    UUID aggregateId();
}
