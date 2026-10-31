package com.ltz.mail.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.mail.domain.enums.MailStatus;
import com.ltz.mail.domain.enums.MailType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Kuyruktaki ve gönderilmiş e-posta kaydı. Satırlar {@code MailMessageRepository#insertIfAbsent} ile eklenir (idempotency
 * için {@code ON CONFLICT}); entity yalnız işleyicinin durum geçişleri içindir. Kod, bağlantı ve tam alıcı adresi
 * yalnız {@code PENDING}/{@code PROCESSING} iken tutulur; terminal durumda silinir (V81 CHECK kısıtı bunu zorlar).
 */
@Entity
@Table(name = "mail_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailMessage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private UUID idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 40)
    private MailType type;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private UUID recipientUserId;

    @Column(name = "recipient_email", length = 254)
    private String recipientEmail;

    @Column(name = "recipient_masked", nullable = false, updatable = false, length = 254)
    private String recipientMasked;

    @Column(name = "locale", updatable = false, length = 2)
    private String locale;

    @Column(name = "payload_encrypted")
    private byte[] payloadEncrypted;

    @Column(name = "payload_key_version", nullable = false, updatable = false)
    private short payloadKeyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MailStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Süresi dolmuş kod/bağlantı gönderilmez; bu kontrol her denemeden önce yapılır. */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** İşleyici kaydı kiralar: durum {@code PROCESSING}, deneme sayısı artar. */
    public void lease(Instant now, Instant until) {
        this.status = MailStatus.PROCESSING;
        this.leaseUntil = until;
        this.attempts++;
        this.updatedAt = now;
    }

    public void markSent(Instant now) {
        this.status = MailStatus.SENT;
        this.sentAt = now;
        this.lastErrorCode = null;
        clearSensitiveData(now);
    }

    /** Geçici hata: kayıt bekleme durumuna döner ve verilen anda yeniden denenir. */
    public void markRetry(Instant now, Instant nextAttempt, String errorCode) {
        this.status = MailStatus.PENDING;
        this.nextAttemptAt = nextAttempt;
        this.leaseUntil = null;
        this.lastErrorCode = errorCode;
        this.updatedAt = now;
    }

    public void markFailed(Instant now, String errorCode) {
        this.status = MailStatus.FAILED;
        this.lastErrorCode = errorCode;
        clearSensitiveData(now);
    }

    private void clearSensitiveData(Instant now) {
        this.recipientEmail = null;
        this.payloadEncrypted = null;
        this.leaseUntil = null;
        this.updatedAt = now;
    }
}
