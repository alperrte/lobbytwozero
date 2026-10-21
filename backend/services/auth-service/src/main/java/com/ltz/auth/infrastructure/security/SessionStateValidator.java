package com.ltz.auth.infrastructure.security;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.AuthSessionRepository;

/**
 * D3 (Auth tarafı): imzası geçerli bir access token, oturumu iptal edilmiş, hesap ACTIVE değil veya token
 * sürümü değişmişse auth-service uçlarında anında reddedilir. Diğer servislerdeki iptal karar kapısındadır.
 */
@Component
public class SessionStateValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2TokenValidatorResult REVOKED = OAuth2TokenValidatorResult.failure(
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Session is no longer valid", null));

    private final AuthSessionRepository sessionRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;

    public SessionStateValidator(AuthSessionRepository sessionRepository, AccountRepository accountRepository,
                                 Clock clock) {
        this.sessionRepository = sessionRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        UUID accountId;
        UUID sessionId;
        Long tokenVersion;
        try {
            accountId = UUID.fromString(jwt.getSubject());
            sessionId = UUID.fromString(jwt.getClaimAsString(TokenClaims.SESSION_ID));
            tokenVersion = jwt.getClaim(TokenClaims.TOKEN_VERSION) instanceof Number number ? number.longValue() : null;
        } catch (IllegalArgumentException | NullPointerException exception) {
            return REVOKED;
        }
        if (tokenVersion == null) {
            return REVOKED;
        }
        Instant now = clock.instant();
        boolean sessionActive = sessionRepository.findById(sessionId)
                .filter(session -> session.getAccountId().equals(accountId) && session.isActiveAt(now))
                .isPresent();
        boolean accountCurrent = sessionActive && accountRepository.findById(accountId)
                .filter(account -> account.isActive() && account.getTokenVersion() == tokenVersion)
                .isPresent();
        return accountCurrent ? OAuth2TokenValidatorResult.success() : REVOKED;
    }
}
