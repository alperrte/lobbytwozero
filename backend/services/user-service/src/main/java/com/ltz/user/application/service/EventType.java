package com.ltz.user.application.service;

import java.util.Optional;

/**
 * Bu servisin işlediği olay türleri ve her birinin tek geçerli üreticisi: bir servis başkasının olayını yayınlayamaz
 * (token'ın {@code sub} değeri {@code svc:<üretici>} olmalıdır).
 */
enum EventType {
    USER_REGISTERED("UserRegistered", Producers.AUTH),
    ACCOUNT_STATUS_CHANGED("AccountStatusChanged", Producers.AUTH),
    ACCOUNT_EMAIL_CHANGED("AccountEmailChanged", Producers.AUTH),
    /** Önerilen sözleşme (T4): Auth'un oturumlu hesaba dış kimlik bağlaması / kaldırması. */
    EXTERNAL_IDENTITY_LINKED("ExternalIdentityLinked", Producers.AUTH),
    EXTERNAL_IDENTITY_UNLINKED("ExternalIdentityUnlinked", Producers.AUTH),
    FRIENDSHIP_CHANGED("FriendshipChanged", Producers.SOCIAL),
    USER_BLOCKED("UserBlocked", Producers.SOCIAL),
    USER_UNBLOCKED("UserUnblocked", Producers.SOCIAL),
    /** Önerilen sözleşme (T7): library-service mutlak koleksiyon sayıları. */
    LIBRARY_SUMMARY_CHANGED("LibrarySummaryChanged", "library-service"),
    /** Önerilen sözleşme (T7): review-service mutlak inceleme sayısı. */
    REVIEW_SUMMARY_CHANGED("ReviewSummaryChanged", "review-service");

    /** Üretici adları; enum sabitlerinden önce başvurulabilmesi için ayrı bir sınıfta. */
    private static final class Producers {
        static final String AUTH = "auth-service";
        static final String SOCIAL = "social-service";

        private Producers() {
        }
    }

    private final String eventName;
    private final String producer;

    EventType(String eventName, String producer) {
        this.eventName = eventName;
        this.producer = producer;
    }

    String producer() {
        return producer;
    }

    static Optional<EventType> find(String eventName) {
        for (EventType type : values()) {
            if (type.eventName.equals(eventName)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
