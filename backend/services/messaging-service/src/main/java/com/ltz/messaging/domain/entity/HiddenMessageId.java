package com.ltz.messaging.domain.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** "Benden sil" anahtarı (MSG-25): kullanıcı + mesaj. */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class HiddenMessageId implements Serializable {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;
}
