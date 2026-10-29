package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.application.port.AccountDeletionMail;
import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.AccountDeletionRequest;
import com.ltz.auth.domain.entity.AccountDeletionTask;
import com.ltz.auth.domain.entity.AccountDeletionToken;
import com.ltz.auth.domain.enums.AccountStatus;
import com.ltz.auth.domain.enums.CodeInvalidationReason;
import com.ltz.auth.domain.enums.DeviceRevokeReason;
import com.ltz.auth.domain.enums.RoleCode;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.domain.enums.SessionRevokeReason;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.config.DeletionProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountDeletionRequestRepository;
import com.ltz.auth.infrastructure.persistence.repository.AccountDeletionTaskRepository;
import com.ltz.auth.infrastructure.persistence.repository.AccountDeletionTokenRepository;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.AuthDataEraser;
import com.ltz.auth.infrastructure.persistence.repository.AuthSessionRepository;
import com.ltz.auth.infrastructure.security.SecureTokens;
import com.ltz.auth.infrastructure.security.VerificationCodeHasher;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * AUTH-16 hesap silme, kullanıcı tarafı. İki adım:
 * <ol>
 *   <li><b>İstek:</b> yeniden kimlik doğrulama (parola; parolasızsa yakın zamanlı giriş; TOTP etkinse ikinci faktör)
 *       ardından e-postaya tek kullanımlık, süreli onay token'ı.</li>
 *   <li><b>Onay:</b> token + kullanıcı adını yazma (açık onay). Hesap DELETION_REQUESTED olur, tüm oturumlar iptal
 *       edilir ve her veri sahibi servis için bir silme görevi açılır (koordinasyon: {@link AccountDeletionCoordinator}).</li>
 * </ol>
 * Hesap bu aşamada fiilen silinmez; tüm servisler silmeyi onaylayınca anonimleştirilir.
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    static final String THROTTLE_DOMAIN = "ACCOUNT_DELETION";
    static final String CANCEL_THROTTLE_DOMAIN = "ACCOUNT_DELETION_CANCEL";
    private static final String TOKEN_FRAGMENT = "#token=";

    private final AccountRepository accountRepository;
    private final AuthSessionRepository sessionRepository;
    private final DeviceRevoker deviceRevoker;
    private final AccountDeletionTokenRepository tokenRepository;
    private final AccountDeletionRequestRepository requestRepository;
    private final AccountDeletionTaskRepository taskRepository;
    private final PasswordEncoder passwordEncoder;
    private final TwoFactorService twoFactorService;
    private final AttemptThrottleService attemptThrottle;
    private final AccountDeletionDispatcher dispatcher;
    private final AuthDataEraser authDataEraser;
    private final VerificationCodeHasher hasher;
    private final SecureTokens secureTokens;
    private final SecurityEventRecorder securityEventRecorder;
    private final DeletionProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    /** {@link #initDummyPasswordHash()} ile açılışta üretilir. */
    private String dummyPasswordHash;

    public void requestDeletion(UUID accountId, UUID sessionId, String password, String code, String recoveryCode) {
        String throttleKey = hasher.hash(THROTTLE_DOMAIN, accountId.toString());
        attemptThrottle.reserve(throttleKey);
        ReauthContext context = transactionTemplate.execute(status -> loadContext(accountId, sessionId));
        verifyReauthentication(accountId, context, password, code, recoveryCode);
        transactionTemplate.execute(status -> issueToken(accountId, context.email())).ifPresent(dispatcher::dispatch);
        attemptThrottle.clear(throttleKey);
    }

    public void confirmDeletion(UUID accountId, String rawToken, String username) {
        String tokenHash = secureTokens.sha256Hex(rawToken);
        boolean confirmed = Boolean.TRUE.equals(transactionTemplate.execute(
                status -> applyConfirmation(accountId, tokenHash, username)));
        if (!confirmed) {
            securityEventRecorder.recordEvent(SecurityEventType.ACCOUNT_DELETION_REJECTED, accountId);
            throw new AuthException(AuthErrorCode.DELETION_CONFIRMATION_INVALID);
        }
    }

    /**
     * Bekleme süresi içinde vazgeçme. Oturum açılamadığı için (hesap DELETION_REQUESTED) kimlik doğrulama giriş gibidir:
     * e-posta/kullanıcı adı + parola (+ TOTP etkinse ikinci faktör), hesap bazında hız sınırlı; bilinmeyen hesapta da parola
     * hash'i doğrulanır. Parolasız (yalnız sosyal girişli) hesap bu yoldan iptal edemez. Başarıda oturum AÇILMAZ; kullanıcı
     * normal girişle devam eder.
     */
    public void cancelDeletion(String identifier, String password, String code, String recoveryCode) {
        String normalized = identifier.indexOf('@') >= 0
                ? Account.normalizeEmail(identifier) : Account.normalizeUsername(identifier);
        String throttleKey = hasher.hash(CANCEL_THROTTLE_DOMAIN, normalized);
        attemptThrottle.reserve(throttleKey);
        Account account = transactionTemplate.execute(status -> (normalized.indexOf('@') >= 0
                ? accountRepository.findWithRolesByEmailNormalized(normalized)
                : accountRepository.findWithRolesByUsernameNormalized(normalized)).orElse(null));
        String passwordHash = account == null ? null : account.passwordHash().orElse(null);
        boolean matches = passwordEncoder.matches(password, passwordHash == null ? dummyPasswordHash : passwordHash)
                && passwordHash != null;
        if (!matches) {
            securityEventRecorder.recordEvent(SecurityEventType.ACCOUNT_DELETION_REJECTED,
                    account == null ? null : account.getId());
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        UUID accountId = account.getId();
        if (twoFactorService.isEnabled(accountId)
                && !twoFactorService.verifySecondFactorForReauthentication(accountId, code, recoveryCode)) {
            rejectReauthentication(accountId);
            throw new AuthException(AuthErrorCode.INVALID_TWO_FACTOR_CODE);
        }
        transactionTemplate.executeWithoutResult(status -> applyCancellation(accountId));
        attemptThrottle.clear(throttleKey);
    }

    /** Tek işlemde: hesap ve talep kilitlenir, süre ve durum yeniden denetlenir; görevler silinir, hesap ACTIVE olur. */
    private void applyCancellation(UUID accountId) {
        Instant now = clock.instant();
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.DELETION_NOT_PENDING));
        AccountDeletionRequest request = requestRepository.findByAccountId(accountId)
                .filter(AccountDeletionRequest::isCoordinating).orElse(null);
        if (account.getStatus() != AccountStatus.DELETION_REQUESTED || request == null) {
            throw new AuthException(AuthErrorCode.DELETION_NOT_PENDING);
        }
        if (account.isIdentityAnonymized() || !now.isBefore(request.getScheduledFor())) {
            throw new AuthException(AuthErrorCode.DELETION_CANCEL_WINDOW_CLOSED);
        }
        taskRepository.deleteByRequestId(request.getId());
        request.cancel(now);
        account.cancelDeletion(now);
        securityEventRecorder.recordEvent(SecurityEventType.ACCOUNT_DELETION_CANCELLED, accountId);
    }

    @PostConstruct
    void initDummyPasswordHash() {
        // Bilinmeyen hesapta da aynı maliyetli doğrulamayı yapmak için (yanıt süresi hesap varlığını belli etmez).
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    private ReauthContext loadContext(UUID accountId, UUID sessionId) {
        Account account = accountRepository.findWithRolesById(accountId).filter(Account::isActive)
                .orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID));
        Instant sessionStartedAt = sessionRepository.findById(sessionId)
                .filter(session -> session.getAccountId().equals(accountId))
                .orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID)).getCreatedAt();
        return new ReauthContext(account.getEmail(), account.passwordHash().orElse(null), account.roleCodes(),
                sessionStartedAt);
    }

    private void verifyReauthentication(UUID accountId, ReauthContext context, String password, String code,
                                        String recoveryCode) {
        // Son ADMIN'in kendini silip yönetimi kilitlemesi önlenir; önce rol kaldırılmalıdır.
        if (context.roles().contains(RoleCode.ADMIN)) {
            throw new AuthException(AuthErrorCode.ADMIN_DELETION_FORBIDDEN);
        }
        if (context.passwordHash() != null) {
            if (password == null || !passwordEncoder.matches(password, context.passwordHash())) {
                rejectReauthentication(accountId);
                throw new AuthException(AuthErrorCode.CURRENT_PASSWORD_INVALID);
            }
        } else if (!clock.instant().isBefore(context.sessionStartedAt().plus(properties.recentLoginWindow()))) {
            // Parolası olmayan (sosyal) hesapta yeniden doğrulama: yakın zamanda sağlayıcıyla yeniden giriş.
            throw new AuthException(AuthErrorCode.REAUTHENTICATION_REQUIRED);
        }
        if (twoFactorService.isEnabled(accountId)
                && !twoFactorService.verifySecondFactorForReauthentication(accountId, code, recoveryCode)) {
            rejectReauthentication(accountId);
            throw new AuthException(AuthErrorCode.INVALID_TWO_FACTOR_CODE);
        }
    }

    private void rejectReauthentication(UUID accountId) {
        securityEventRecorder.recordEvent(SecurityEventType.ACCOUNT_DELETION_REJECTED, accountId);
    }

    private Optional<AccountDeletionMail> issueToken(UUID accountId, String email) {
        Instant now = clock.instant();
        boolean coolingDown = tokenRepository.findFirstByAccountIdOrderByCreatedAtDesc(accountId)
                .map(latest -> now.isBefore(latest.getCreatedAt().plus(properties.requestCooldown())))
                .orElse(false);
        if (coolingDown) {
            return Optional.empty();
        }
        String rawToken = secureTokens.newOpaqueToken();
        tokenRepository.invalidateActive(accountId, CodeInvalidationReason.SUPERSEDED, now);
        AccountDeletionToken saved = tokenRepository.save(AccountDeletionToken.issue(accountId,
                secureTokens.sha256Hex(rawToken), now, now.plus(properties.tokenTtl())));
        securityEventRecorder.recordEvent(SecurityEventType.ACCOUNT_DELETION_REQUESTED, accountId);
        return Optional.of(new AccountDeletionMail(saved.getId(), accountId, email,
                properties.linkBaseUrl() + TOKEN_FRAGMENT + rawToken, properties.tokenTtl()));
    }

    /** Tek işlemde: token tüketimi, durum geçişi, talep + görevler, oturum iptali. Token oturumun hesabına bağlıdır. */
    private boolean applyConfirmation(UUID accountId, String tokenHash, String username) {
        Instant now = clock.instant();
        AccountDeletionToken token = tokenRepository.findByTokenHashForUpdate(tokenHash)
                .filter(candidate -> candidate.getAccountId().equals(accountId) && candidate.isUsableAt(now))
                .orElse(null);
        if (token == null) {
            return false;
        }
        Account account = accountRepository.findByIdForUpdate(accountId)
                .filter(Account::isActive)
                .filter(candidate -> candidate.getUsernameNormalized().equals(Account.normalizeUsername(username)))
                .orElse(null);
        if (account == null) {
            return false;
        }
        token.consume(now);
        account.requestDeletion(now);
        // Hesap başına tek talep: tekrarlanan onay yeni talep/görev üretmez (idempotent). İptal edilmiş talep yeniden açılır.
        Instant scheduledFor = now.plus(properties.gracePeriod());
        AccountDeletionRequest existing = requestRepository.findByAccountId(accountId).orElse(null);
        if (existing == null) {
            openRequest(accountId, now, scheduledFor);
        } else if (existing.isCancelled()) {
            existing.reopen(scheduledFor);
            createTasks(existing.getId(), accountId, now, scheduledFor);
        }
        securityEventRecorder.recordEvent(SecurityEventType.ACCOUNT_DELETION_CONFIRMED, accountId);
        // Toplu güncelleme persistence context'i temizler; bekleyen değişiklikler önce flush edilir.
        sessionRepository.revokeAllActive(accountId, SessionRevokeReason.ACCOUNT_DELETION, now);
        deviceRevoker.revokeAll(accountId, DeviceRevokeReason.ACCOUNT_DELETION, now);
        // Silinecek hesabın bekleyen sıfırlama/doğrulama/MFA/Steam artıkları artık geçersiz kılınır.
        authDataEraser.purgeEphemeral(accountId);
        return true;
    }

    private void openRequest(UUID accountId, Instant now, Instant scheduledFor) {
        AccountDeletionRequest request = requestRepository.save(AccountDeletionRequest.open(accountId, now, scheduledFor));
        createTasks(request.getId(), accountId, now, scheduledFor);
    }

    /** Görevler bekleme süresi bitene kadar işlenmez ({@code next_attempt_at = scheduledFor}). */
    private void createTasks(UUID requestId, UUID accountId, Instant now, Instant scheduledFor) {
        for (String service : properties.targets()) {
            taskRepository.save(AccountDeletionTask.create(requestId, accountId, service, now, scheduledFor));
        }
    }

    private record ReauthContext(String email, String passwordHash, Set<RoleCode> roles, Instant sessionStartedAt) {

        @Override
        public String toString() {
            return "ReauthContext[email=***, passwordHash=***, roles=" + roles + ", sessionStartedAt="
                    + sessionStartedAt + "]";
        }
    }
}
