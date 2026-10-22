package com.ltz.auth.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.domain.enums.SecurityEventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** AUTH-15 güvenlik olayı. Yalnız olay türü ve hesap kimliği tutulur; kişisel veri veya sır yazılmaz. */
@Entity
@Table(name = "security_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 64)
    private SecurityEventType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** İstek korelasyon kimliği (X-Request-Id); istek dışı (asenkron) olaylarda boş. */
    @Column(name = "request_id", updatable = false, length = 64)
    private String requestId;

    public static SecurityEvent of(SecurityEventType type, UUID accountId, String requestId, Instant now) {
        SecurityEvent event = new SecurityEvent();
        event.type = type;
        event.accountId = accountId;
        event.requestId = requestId;
        event.createdAt = now;
        return event;
    }
}
