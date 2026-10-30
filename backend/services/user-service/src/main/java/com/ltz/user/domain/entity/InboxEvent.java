package com.ltz.user.domain.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * İşlenmiş bir olayın tekilleştirme kaydı (docs/architecture.md §6.2). Olay yükü ve hesap kimliği saklanmaz; kayıt
 * yalnızca {@code eventId}'nin ikinci kez uygulanmasını önler. Satırlar {@code InboxEventRepository#recordIfNew} ile
 * eklenir.
 */
@Entity
@Table(name = "inbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 128)
    private String eventType;

    @Column(name = "producer", nullable = false, updatable = false, length = 64)
    private String producer;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;
}
