package com.ltz.mail.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.mail.domain.enums.AttemptResult;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tek gönderim denemesinin kaydı (MAIL-15). Sağlayıcı hata metni, alıcı adresi ve içerik saklanmaz; yalnız sonuç,
 * SMTP durum kodu ve sabit bir hata kodu tutulur.
 */
@Entity
@Table(name = "mail_delivery_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailDeliveryAttempt {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "attempt_no", nullable = false, updatable = false)
    private int attemptNo;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, updatable = false, length = 24)
    private AttemptResult result;

    @Column(name = "smtp_code", updatable = false)
    private Integer smtpCode;

    @Column(name = "error_code", updatable = false, length = 64)
    private String errorCode;

    @Column(name = "duration_ms", nullable = false, updatable = false)
    private int durationMs;

    public MailDeliveryAttempt(UUID messageId, int attemptNo, Instant attemptedAt, AttemptResult result,
                               Integer smtpCode, String errorCode, int durationMs) {
        this.id = UUID.randomUUID();
        this.messageId = messageId;
        this.attemptNo = attemptNo;
        this.attemptedAt = attemptedAt;
        this.result = result;
        this.smtpCode = smtpCode;
        this.errorCode = errorCode;
        this.durationMs = durationMs;
    }
}
