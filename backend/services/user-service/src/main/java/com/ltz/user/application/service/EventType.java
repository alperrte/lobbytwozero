package com.ltz.user.application.service;

import java.util.Optional;

/**
 * Bu servisin işlediği olay türleri ve her birinin tek geçerli üreticisi: bir servis başkasının olayını yayınlayamaz
 * (token'ın {@code sub} değeri {@code svc:<üretici>} olmalıdır).
 */
enum EventType {
    USER_REGISTERED("UserRegistered", "auth-service"),
    ACCOUNT_STATUS_CHANGED("AccountStatusChanged", "auth-service"),
    ACCOUNT_EMAIL_CHANGED("AccountEmailChanged", "auth-service"),
    FRIENDSHIP_CHANGED("FriendshipChanged", "social-service"),
    USER_BLOCKED("UserBlocked", "social-service"),
    USER_UNBLOCKED("UserUnblocked", "social-service"),
    /** Önerilen sözleşme (T7): library-service mutlak koleksiyon sayıları. */
    LIBRARY_SUMMARY_CHANGED("LibrarySummaryChanged", "library-service"),
    /** Önerilen sözleşme (T7): review-service mutlak inceleme sayısı. */
    REVIEW_SUMMARY_CHANGED("ReviewSummaryChanged", "review-service");

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
