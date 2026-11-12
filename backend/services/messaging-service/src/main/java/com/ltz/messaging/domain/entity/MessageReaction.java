package com.ltz.messaging.domain.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Mesaja bırakılan emoji tepkisi (MSG-23). Yazma ve değiştirme koşullu upsert ile yapılır (yarışta tek satır kalır);
 * entity yalnız okunur.
 */
@Entity
@Table(name = "message_reactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageReaction {

    @EmbeddedId
    private MessageReactionId id;

    @Column(name = "emoji", nullable = false, updatable = false, length = 32)
    private String emoji;

    @Column(name = "reacted_at", nullable = false, updatable = false)
    private Instant reactedAt;
}
