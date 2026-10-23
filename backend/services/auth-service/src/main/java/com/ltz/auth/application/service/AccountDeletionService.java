package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Instant;
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
import com.ltz.auth.domain.enums.CodeInvalidationReason;
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
    private static final String TOKEN_FRAGMENT = "#token=";

    private final AccountRepository accountRepository;
    private final AuthSessionRepository sessionRepository;
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

    public void requestDeletion(UUID accountId, UUID sessionId, String password, String code, String recoveryCode) {
        String throttleKey = hasher.hash(THROTTLE_DOMAIN, accountId.toString());
        attemptThrottle.reserve(throttleKey);
        ReauthContext context = transactionTemplate.execute(status -> loadContext(accountId, sessionId));
        verifyReauthentication(accountId, context, password, code, recoveryCode);
        AccountDeletionMail mail = transactionTemplate.execute(status -> issueToken(accountId, context.email()));
        if (mail != null) {
            dispatcher.dispatch(mail);
        }
        attemptThrottle.clear(throttleKey);
    }

    public void confirmDeletion(UUID accountId, String rawToken, String username) {
        String tokenHash = secureTokens.sha256Hex(rawToken);
        boolean confirmed = Boolean.TRUE.equals(transactionTemplate.execute(
                status -> applyConfirmation(accountId, tokenHash, username)));
        if (!confirmed) {
            securityEventRecorder.record(SecurityEventType.ACCOUNT_DELETION_REJECTED, accountId);
            throw new AuthException(AuthErrorCode.DELETION_CONFIRMATION_INVALID);
        }
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
        securityEventRecorder.record(SecurityEventType.ACCOUNT_DELETION_REJECTED, accountId);
    }

    private AccountDeletionMail issueToken(UUID accountId, String email) {
        Instant now = clock.instant();
        boolean coolingDown = tokenRepository.findFirstByAccountIdOrderByCreatedAtDesc(accountId)
                .map(latest -> now.isBefore(latest.getCreatedAt().plus(properties.requestCooldown())))
                .orElse(false);
        if (coolingDown) {
            return null;
        }
        String rawToken = secureTokens.newOpaqueToken();
        tokenRepository.invalidateActive(accountId, CodeInvalidationReason.SUPERSEDED, now);
        AccountDeletionToken saved = tokenRepository.save(AccountDeletionToken.issue(accountId,
                secureTokens.sha256Hex(rawToken), now, now.plus(properties.tokenTtl())));
        securityEventRecorder.record(SecurityEventType.ACCOUNT_DELETION_REQUESTED, accountId);
        return new AccountDeletionMail(saved.getId(), accountId, email,
                properties.linkBaseUrl() + TOKEN_FRAGMENT + rawToken, properties.tokenTtl());
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
        // Hesap başına tek talep: tekrarlanan onay yeni talep/görev üretmez (idempotent).
        if (requestRepository.findByAccountId(accountId).isEmpty()) {
            openRequest(accountId, now);
        }
        securityEventRecorder.record(SecurityEventType.ACCOUNT_DELETION_CONFIRMED, accountId);
        // Toplu güncelleme persistence context'i temizler; bekleyen değişiklikler önce flush edilir.
        sessionRepository.revokeAllActive(accountId, SessionRevokeReason.ACCOUNT_DELETION, now);
        // Silinecek hesabın bekleyen sıfırlama/doğrulama/MFA/Steam artıkları artık geçersiz kılınır.
        authDataEraser.purgeEphemeral(accountId);
        return true;
    }

    private AccountDeletionRequest openRequest(UUID accountId, Instant now) {
        AccountDeletionRequest request = requestRepository.save(AccountDeletionRequest.open(accountId, now));
        for (String service : properties.targets()) {
            taskRepository.save(AccountDeletionTask.create(request.getId(), accountId, service, now));
        }
        return request;
    }

    private record ReauthContext(String email, String passwordHash, Set<RoleCode> roles, Instant sessionStartedAt) {

        @Override
        public String toString() {
            return "ReauthContext[email=***, passwordHash=***, roles=" + roles + ", sessionStartedAt="
                    + sessionStartedAt + "]";
        }
    }
}
