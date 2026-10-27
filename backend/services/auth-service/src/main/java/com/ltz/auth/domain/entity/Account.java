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
    private static final String DELETED_EMAIL_PREFIX = "deleted-";
    private static final String DELETED_EMAIL_DOMAIN = "@deleted.invalid";
    private static final String DELETED_USERNAME_PREFIX = "d_";
    private static final int DELETED_USERNAME_ID_LENGTH = 12;
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

    /**
     * Fiili silme aşaması: kişisel alanlar geri döndürülemez biçimde anonimleştirilir. Hesap iskeleti (kimlik) diğer
     * servislerin REF bütünlüğü ve denetim kayıtları için kalır.
     */
    public void anonymize(Instant now) {
        String compactId = id.toString().replace("-", "");
        email = DELETED_EMAIL_PREFIX + id + DELETED_EMAIL_DOMAIN;
        emailNormalized = email;
        username = DELETED_USERNAME_PREFIX + compactId.substring(0, DELETED_USERNAME_ID_LENGTH);
        usernameNormalized = username;
        passwordHash = null;
        emailVerifiedAt = null;
        roles.clear();
        status = AccountStatus.DELETED;
        invalidateAllTokens(now);
    }

    /** Silme onaylandı: hesap kapanır, önceki tüm token'lar düşer; veri servisler onaylayınca anonimleştirilir. */
    public void requestDeletion(Instant now) {
        status = AccountStatus.DELETION_REQUESTED;
        invalidateAllTokens(now);
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

    /** Durum geçerliliği çağıranda (AccountStatus.canTransitionTo) denetlenir; ACTIVE'den çıkış eski token'ları düşürür. */
    public void changeStatus(AccountStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Status transition is not allowed");
        }
        status = target;
        updatedAt = now;
        if (target != AccountStatus.ACTIVE) {
            tokenVersion++;
        }
    }

    /** Rol değişikliği eski rolleri taşıyan token'ları düşürür (token sürümü artar). */
    public void grantRole(Role role, Instant now) {
        roles.add(role);
        invalidateAllTokens(now);
    }

    public void revokeRole(RoleCode code, Instant now) {
        roles.removeIf(role -> role.getCode() == code);
        invalidateAllTokens(now);
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

    /** Parola değişince önceki tüm access token'lar da geçersiz olur (token sürümü artar). */
    public void changePassword(String newPasswordHash, Instant now) {
        passwordHash = newPasswordHash;
        invalidateAllTokens(now);
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
