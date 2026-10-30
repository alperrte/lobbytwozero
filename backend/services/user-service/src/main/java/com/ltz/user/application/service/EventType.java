package com.ltz.user.application.service;

import java.util.Optional;

/**
 * Bu servisin işlediği olay türleri ve her birinin tek geçerli üreticisi: bir servis başkasının olayını yayınlayamaz
 * (token'ın {@code sub} değeri {@code svc:<üretici>} olmalıdır).
 */
enum EventType {
    USER_REGISTERED("UserRegistered", "auth-service"),
    ACCOUNT_STATUS_CHANGED("AccountStatusChanged", "auth-service"),
    ACCOUNT_EMAIL_CHANGED("AccountEmailChanged", "auth-service");

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
