package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.AuthSession;
import com.ltz.auth.domain.entity.RefreshToken;
import com.ltz.auth.domain.enums.AuthMethod;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.domain.enums.SessionRevokeReason;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.config.SessionProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.AuthSessionRepository;
import com.ltz.auth.infrastructure.persistence.repository.RefreshTokenRepository;
import com.ltz.auth.infrastructure.security.AccessTokenIssuer;
import com.ltz.auth.infrastructure.security.AccessTokenIssuer.IssuedAccessToken;
import com.ltz.auth.infrastructure.security.SecureTokens;

import lombok.RequiredArgsConstructor;

/**
 * AUTH-07/08: oturum açma, refresh rotation, reuse tespiti ve çıkış.
 * <ul>
 *   <li>Refresh token satırı kilitlenir; eşzamanlı iki yenilemeden yalnız biri döndürme yapar.</li>
 *   <li>Döndürülmüş token kısa süre (grace) içinde gelirse 409 döner, oturum korunur (çoklu sekme).</li>
 *   <li>Grace sonrası tekrar kullanım çalıntı sayılır: oturum ailesi tümüyle iptal edilir.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private final AuthSessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountRepository accountRepository;
    private final AccessTokenIssuer accessTokenIssuer;
    private final SecureTokens secureTokens;
    private final SessionProperties properties;
    private final SecurityEventRecorder securityEventRecorder;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** Yalnız ACTIVE hesap için oturum açar; çağıranın işlemine katılır. */
    @Transactional
    public IssuedSession start(UUID accountId, List<AuthMethod> methods) {
        Account account = accountRepository.findWithRolesById(accountId)
                .filter(Account::isActive)
                .orElseThrow(() -> new AuthException(AuthErrorCode.ACCOUNT_UNAVAILABLE));
        Instant now = clock.instant();
        AuthSession session = sessionRepository.save(AuthSession.open(accountId, account.getTokenVersion(), methods,
                now, now.plus(properties.absoluteTtl())));
        IssuedSession issued = issueTokens(account, session, now);
        securityEventRecorder.recordEvent(SecurityEventType.LOGIN_SUCCEEDED, accountId);
        return issued;
    }

    public IssuedSession refresh(String rawRefreshToken) {
        RefreshOutcome outcome = transactionTemplate.execute(status -> rotate(rawRefreshToken));
        return switch (outcome) {
            case Rotated(IssuedSession session) -> session;
            case Conflict _ -> throw new AuthException(AuthErrorCode.REFRESH_CONFLICT);
            case Rejected _ -> throw new AuthException(AuthErrorCode.SESSION_INVALID);
        };
    }

    /** Oturumu (refresh ailesini) iptal eder. Bilinmeyen veya zaten iptal edilmiş token için sessizce döner. */
    public void logout(String rawRefreshToken) {
        transactionTemplate.executeWithoutResult(status -> refreshTokenRepository
                .findByTokenHash(secureTokens.sha256Hex(rawRefreshToken))
                .flatMap(token -> sessionRepository.findByIdForUpdate(token.getSessionId()))
                .filter(session -> session.getRevokedAt() == null)
                .ifPresent(session -> {
                    session.revoke(SessionRevokeReason.LOGOUT, clock.instant());
                    securityEventRecorder.recordEvent(SecurityEventType.LOGOUT, session.getAccountId());
                }));
    }

    /** Tüm cihazlardan çıkış: tüm oturumlar iptal edilir ve token sürümü artar (eski access token'lar da düşer). */
    @Transactional
    public void logoutAll(UUID accountId) {
        Instant now = clock.instant();
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID));
        account.invalidateAllTokens(now);
        sessionRepository.revokeAllActive(accountId, SessionRevokeReason.LOGOUT_ALL, now);
        securityEventRecorder.recordEvent(SecurityEventType.LOGOUT_ALL, accountId);
    }

    private RefreshOutcome rotate(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(secureTokens.sha256Hex(rawRefreshToken))
                .orElse(null);
        if (token == null) {
            return new Rejected();
        }
        AuthSession session = sessionRepository.findByIdForUpdate(token.getSessionId()).orElseThrow();
        if (!session.isActiveAt(now)) {
            return reject(session);
        }
        if (token.isRotated()) {
            if (token.wasRotatedWithin(properties.refreshReuseGrace(), now)) {
                return new Conflict();
            }
            session.revoke(SessionRevokeReason.REFRESH_REUSE, now);
            securityEventRecorder.recordEvent(SecurityEventType.REFRESH_TOKEN_REUSE_DETECTED, session.getAccountId());
            return new Rejected();
        }
        if (token.isExpiredAt(now)) {
            return reject(session);
        }
        Account account = accountRepository.findWithRolesById(session.getAccountId()).orElseThrow();
        if (!account.isActive() || account.getTokenVersion() != session.getTokenVersion()) {
            session.revoke(SessionRevokeReason.ACCOUNT_INACTIVE, now);
            return reject(session);
        }
        token.rotate(now);
        IssuedSession issued = issueTokens(account, session, now);
        securityEventRecorder.recordEvent(SecurityEventType.SESSION_REFRESHED, account.getId());
        return new Rotated(issued);
    }

    private Rejected reject(AuthSession session) {
        securityEventRecorder.recordEvent(SecurityEventType.SESSION_REJECTED, session.getAccountId());
        return new Rejected();
    }

    private IssuedSession issueTokens(Account account, AuthSession session, Instant now) {
        String rawRefreshToken = secureTokens.newOpaqueToken();
        Instant refreshExpiresAt = earliest(now.plus(properties.refreshTokenTtl()), session.getExpiresAt());
        refreshTokenRepository.save(RefreshToken.issue(session.getId(), secureTokens.sha256Hex(rawRefreshToken),
                now, refreshExpiresAt));
        IssuedAccessToken accessToken = accessTokenIssuer.issue(account, session, now);
        return new IssuedSession(accessToken.value(), accessToken.expiresAt(), rawRefreshToken, refreshExpiresAt);
    }

    private static Instant earliest(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private sealed interface RefreshOutcome permits Rotated, Conflict, Rejected {
    }

    private record Rotated(IssuedSession session) implements RefreshOutcome {
    }

    private record Conflict() implements RefreshOutcome {
    }

    private record Rejected() implements RefreshOutcome {
    }
}
