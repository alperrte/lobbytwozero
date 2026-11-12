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
 * Kullanıcının kendi görünümünden kaldırdığı mesaj (MSG-25). Diğer katılımcıları etkilemez; okundu bilgisini değiştirmez.
 * Yazma idempotent native insert ile yapılır; entity yalnız sorgularda kullanılır.
 */
@Entity
@Table(name = "message_hidden_for_users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HiddenMessage {

    @EmbeddedId
    private HiddenMessageId id;

    @Column(name = "hidden_at", nullable = false, updatable = false)
    private Instant hiddenAt;
}
