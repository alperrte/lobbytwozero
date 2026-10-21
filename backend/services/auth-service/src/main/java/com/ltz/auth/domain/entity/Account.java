package com.ltz.auth.domain.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.ltz.auth.domain.enums.AccountStatus;
import com.ltz.auth.domain.enums.RoleCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    public static final int EMAIL_MAX_LENGTH = 254;
    public static final int USERNAME_MIN_LENGTH = 3;
    public static final int USERNAME_MAX_LENGTH = 20;
    /** Görünen biçim korunur; tekillik küçük harfe çevrilmiş biçim üzerinden sağlanır. */
    public static final String USERNAME_PATTERN =
            "^[A-Za-z0-9_]{" + USERNAME_MIN_LENGTH + "," + USERNAME_MAX_LENGTH + "}$";
    /** Uzunluk UTF-16 karakteri olarak ölçülür; parola içeriği dönüştürülmez veya normalize edilmez. */
    public static final int PASSWORD_MIN_LENGTH = 12;
    public static final int PASSWORD_MAX_LENGTH = 128;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = EMAIL_MAX_LENGTH)
    private String email;

    @Column(name = "email_normalized", nullable = false, length = EMAIL_MAX_LENGTH)
    private String emailNormalized;

    @Column(nullable = false, length = USERNAME_MAX_LENGTH)
    private String username;

    @Column(name = "username_normalized", nullable = false, length = USERNAME_MAX_LENGTH)
    private String usernameNormalized;

    @Getter(AccessLevel.NONE)
    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountStatus status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Artırıldığında hesabın önceki tüm oturumları ve access token'ları geçersiz sayılır (JWT "ver"). */
    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    @Version
    private long version;

    @Getter(AccessLevel.NONE)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "account_roles",
            joinColumns = @JoinColumn(name = "account_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    public static Account registerPending(String email, String username, String passwordHash,
                                          Role initialRole, Instant now) {
        Account account = new Account();
        account.email = email.strip();
        account.emailNormalized = normalizeEmail(email);
        account.username = username;
        account.usernameNormalized = normalizeUsername(username);
        account.passwordHash = passwordHash;
        account.status = AccountStatus.PENDING_VERIFICATION;
        account.createdAt = now;
        account.updatedAt = now;
        account.roles.add(initialRole);
        return account;
    }

    /** Sağlayıcının doğruladığı e-postayla (Google) açılan, parolasız ve doğrudan ACTIVE hesap. */
    public static Account registerVerifiedExternal(String email, String username, Role initialRole, Instant now) {
        Account account = registerPending(email, username, null, initialRole, now);
        account.markEmailVerified(now);
        return account;
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public static String normalizeUsername(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }

    public boolean isPendingVerification() {
        return status == AccountStatus.PENDING_VERIFICATION;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    /** Harici kimlikle (Faz 3) açılan hesapta parola yoktur. */
    public Optional<String> passwordHash() {
        return Optional.ofNullable(passwordHash);
    }

    public Set<RoleCode> roleCodes() {
        return roles.stream().map(Role::getCode).collect(Collectors.toUnmodifiableSet());
    }

    public void invalidateAllTokens(Instant now) {
        tokenVersion++;
        updatedAt = now;
    }

    public void markEmailVerified(Instant now) {
        if (!isPendingVerification()) {
            throw new IllegalStateException("Only pending accounts can be verified");
        }
        status = AccountStatus.ACTIVE;
        emailVerifiedAt = now;
        updatedAt = now;
    }
}
