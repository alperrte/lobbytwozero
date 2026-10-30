package com.ltz.user.application.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ltz.user.infrastructure.persistence.repository.ProfileCounterRepository;
import com.ltz.user.infrastructure.persistence.repository.SocialProjectionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Diğer servislerden gelen ve yalnız yansıma güncelleyen olaylar (Social arkadaşlık/engel, Library/Review özet sayıları).
 * Her yazma son-durum upsert'üdür ve olayın {@code occurredAt} zamanıyla korunur: sırası bozuk eski olay yeni durumu
 * ezmez. Çağıran ({@link EventIngestService}) olayı aynı transaction'da inbox'a yazar.
 */
@Component
@RequiredArgsConstructor
class ProjectionEventHandler {

    private final SocialProjectionRepository social;
    private final ProfileCounterRepository counters;

    /** {@code FriendshipChanged {userLowId, userHighId, change: ADDED|REMOVED}} (social-service, SOC-13). */
    void friendshipChanged(IncomingEvent event) {
        UUID low = EventPayload.uuid(event.payload(), "userLowId");
        UUID high = EventPayload.uuid(event.payload(), "userHighId");
        String change = EventPayload.text(event.payload(), "change");
        if (low.equals(high) || !("ADDED".equals(change) || "REMOVED".equals(change))) {
            throw EventPayload.invalid();
        }
        social.upsertFriendship(low, high, "ADDED".equals(change), event.occurredAt());
    }

    /** {@code UserBlocked} / {@code UserUnblocked {blockerId, blockedId}} (social-service, SOC-09/10). */
    void blockChanged(IncomingEvent event, boolean active) {
        UUID blocker = EventPayload.uuid(event.payload(), "blockerId");
        UUID blocked = EventPayload.uuid(event.payload(), "blockedId");
        if (blocker.equals(blocked)) {
            throw EventPayload.invalid();
        }
        social.upsertBlock(blocker, blocked, active, event.occurredAt());
    }

    /** Önerilen: {@code LibrarySummaryChanged {userId, collectionCount, completedCount, favoriteCount}}; userId = aggregateId. */
    void librarySummaryChanged(IncomingEvent event) {
        UUID userId = subject(event);
        counters.upsertLibrary(userId, EventPayload.count(event.payload(), "collectionCount"),
                EventPayload.count(event.payload(), "completedCount"),
                EventPayload.count(event.payload(), "favoriteCount"), event.occurredAt());
    }

    /** Önerilen: {@code ReviewSummaryChanged {userId, reviewCount}}; userId = aggregateId. */
    void reviewSummaryChanged(IncomingEvent event) {
        UUID userId = subject(event);
        counters.upsertReviews(userId, EventPayload.count(event.payload(), "reviewCount"), event.occurredAt());
    }

    private static UUID subject(IncomingEvent event) {
        UUID userId = EventPayload.uuid(event.payload(), "userId");
        if (!userId.equals(event.aggregateId())) {
            throw EventPayload.invalid();
        }
        return userId;
    }
}
