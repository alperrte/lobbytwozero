package com.ltz.messaging.domain.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Üyenin okuduğunu bildirdiği kapalı mesaj aralığı {@code [fromSeq, toSeq]} ve okuma zamanı (MSG-11, MSG-12).
 * Aralık, üyenin okuma işaretçisi her ilerlediğinde bir kez yazılır ve sonradan değişmez. Okuyan başka servise
 * ait olduğundan yalnız kimlikle (REF) tutulur.
 */
@Entity
@Table(name = "message_read_ranges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageReadRange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "from_seq", nullable = false, updatable = false)
    private long fromSeq;

    @Column(name = "to_seq", nullable = false, updatable = false)
    private long toSeq;

    @Column(name = "read_at", nullable = false, updatable = false)
    private Instant readAt;

    /** {@code fromSeq <= toSeq} olmalıdır; DB de aynı kuralı zorlar. */
    public static MessageReadRange of(UUID conversationId, UUID userId, long fromSeq, long toSeq, Instant readAt) {
        MessageReadRange range = new MessageReadRange();
        range.conversationId = conversationId;
        range.userId = userId;
        range.fromSeq = fromSeq;
        range.toSeq = toSeq;
        range.readAt = readAt;
        return range;
    }
}
