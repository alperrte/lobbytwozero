package com.ltz.auth.domain.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * İlk kez Google ile gelen kişinin, kullanıcı adını seçene kadar bekleyen kaydı. Doğrulanmış Google "sub" değeri ve
 * Google'ın doğruladığı e-posta SUNUCUDA saklanır; istemci yalnız opak tek kullanımlık token'ı taşır (token özet olarak
 * saklanır). Hesap bu kayıt tamamlanana kadar açılmaz.
 */
@Entity
@Table(name = "google_registrations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoogleRegistration {

    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(nullable = false, updatable = false, length = 64)
    private String subject;

    @Column(nullable = false, updatable = false, length = Account.EMAIL_MAX_LENGTH)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public static GoogleRegistration issue(String tokenHash, String subject, String email, Instant now,
                                           Instant expiresAt) {
        GoogleRegistration registration = new GoogleRegistration();
        registration.tokenHash = tokenHash;
        registration.subject = subject;
        registration.email = email;
        registration.createdAt = now;
        registration.expiresAt = expiresAt;
        return registration;
    }

    public boolean isUsableAt(Instant now) {
        return consumedAt == null && now.isBefore(expiresAt);
    }

    public void consume(Instant now) {
        consumedAt = now;
    }

    @Override
    public String toString() {
        return "GoogleRegistration[subject=***, email=***, expiresAt=" + expiresAt + "]";
    }
}
