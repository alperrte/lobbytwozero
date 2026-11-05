package com.ltz.auth.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.ltz.auth.api.controller.AuthApiPaths;
import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.ExternalIdentity;
import com.ltz.auth.domain.entity.OAuthLinkFlow;
import com.ltz.auth.domain.enums.AuthEventType;
import com.ltz.auth.domain.enums.ExternalProvider;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.config.OAuthProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.ExternalIdentityRepository;
import com.ltz.auth.infrastructure.persistence.repository.OAuthLinkFlowRepository;
import com.ltz.auth.infrastructure.security.SecureTokens;
import com.ltz.auth.infrastructure.security.VerificationCodeHasher;

import lombok.RequiredArgsConstructor;

/**
 * Oturumdaki hesaba Steam ve Google bağlama (AUTH-20). Kimlik kanıtı yalnız Auth'ta üretilir: SteamID64 giriş akışıyla
 * aynı OpenID doğrulamasından ({@link SteamLoginService#verifiedSteamId}), Google kimliği aynı OIDC kod + PKCE
 * doğrulamasından geçer.
 * <ul>
 *   <li><b>Yeniden doğrulama:</b> bağlama giriş yöntemini değiştirir. Cihaz bağlama ve e-posta değiştirmedeki gibi mevcut
 *       parola (TOTP etkinse ikinci faktör) istenir; çalınmış bir oturum kendi kimliğini bağlayıp hesabı sonradan ele
 *       geçiremez. Parolasız hesap bu işlemi yapamaz ({@code PASSWORD_REQUIRED}); önce parola belirlemelidir.</li>
 *   <li><b>Kalıcılık (2026-11-05 kararı):</b> bağlanan kimlik kullanıcı tarafından kaldırılamaz; kaldırma yalnız yönetici
 *       (ticket) yoluyla yapılır, bu yüzden burada kaldırma işlemi yoktur.</li>
 *   <li><b>Hesaba bağlı akış:</b> hangi hesaba bağlanacağı akışa yazılır ({@code oauth_link_flows}); state ayrıca tarayıcı
 *       çerezine bağlıdır. Başkasının başlattığı akış, kurbanın tarayıcısında tamamlanamaz.</li>
 *   <li><b>Tekillik:</b> bir harici kimlik tek hesaba, bir hesap sağlayıcı başına tek kimliğe bağlıdır (veritabanı da zorlar).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ExternalIdentityLinkService {

    private static final String THROTTLE_DOMAIN = "EXTERNAL_IDENTITY_LINK";

    private final SteamLoginService steamLoginService;
    private final GoogleLoginService googleLoginService;
    private final OAuthLinkFlowRepository flowRepository;
    private final ExternalIdentityRepository identityRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TwoFactorService twoFactorService;
    private final AttemptThrottleService attemptThrottle;
    private final VerificationCodeHasher throttleHasher;
    private final SecureTokens secureTokens;
    private final SecurityEventRecorder securityEventRecorder;
    private final AuthEventPublisher eventPublisher;
    private final OAuthProperties properties;
    private final ExternalUrls urls;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** Yeniden doğrulamadan sonra Steam bağlama akışını başlatır; tarayıcı dönen adrese yönlendirilir. */
    public StartedFlow startSteamLink(UUID accountId, String password, String code, String recoveryCode) {
        String state = startFlow(accountId, ExternalProvider.STEAM, password, code, recoveryCode);
        return new StartedFlow(steamLoginService.authorizationUri(returnTo(state)), state);
    }

    /** Yeniden doğrulamadan sonra Google bağlama akışını başlatır (Authorization Code + PKCE). */
    public StartedFlow startGoogleLink(UUID accountId, String password, String code, String recoveryCode) {
        String state = startFlow(accountId, ExternalProvider.GOOGLE, password, code, recoveryCode);
        return new StartedFlow(googleLoginService.authorizationUri(state, googleRedirectUri()), state);
    }

    private String startFlow(UUID accountId, ExternalProvider provider, String password, String code,
                             String recoveryCode) {
        Account account = activeAccount(accountId);
        reauthenticate(account, password, code, recoveryCode);
        if (identityRepository.findByAccountIdAndProvider(accountId, provider).isPresent()) {
            throw new AuthException(AuthErrorCode.EXTERNAL_IDENTITY_ALREADY_LINKED);
        }
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            String newState = secureTokens.newOpaqueToken();
            flowRepository.save(OAuthLinkFlow.start(secureTokens.sha256Hex(newState), accountId, provider,
                    now, now.plus(properties.flowTtl())));
            return newState;
        });
    }

    /**
     * Steam'den dönen yanıtı tamamlar. State, çerezdeki değerle aynı olmalı ve tek kullanımlık olmalıdır; hesap akıştan
     * gelir (oturum çerezine bakılmaz).
     *
     * @return bağlanan hesap
     */
    public UUID completeSteamLink(Map<String, String[]> requestParams, String stateFromCookie) {
        String[] values = requestParams.get("state");
        String state = values != null && values.length == 1 ? values[0] : null;
        UUID accountId = consumeFlow(state, stateFromCookie, ExternalProvider.STEAM);
        String steamId = steamLoginService.verifiedSteamId(requestParams, returnTo(state));
        persistLink(accountId, ExternalProvider.STEAM, steamId);
        return accountId;
    }

    /**
     * Google'dan dönen kodu tamamlar. State çerezle aynı ve tek kullanımlık olmalıdır; hesap akıştan gelir. Kimlik yalnız
     * Google {@code sub} ile eşleşir (e-posta eşleşmesi aranmaz: hesap parolayla zaten yeniden doğrulandı).
     *
     * @return bağlanan hesap
     */
    public UUID completeGoogleLink(String code, String state, String stateFromCookie) {
        UUID accountId = consumeFlow(state, stateFromCookie, ExternalProvider.GOOGLE);
        String subject = googleLoginService.verifiedIdentity(code, state, googleRedirectUri()).subject();
        persistLink(accountId, ExternalProvider.GOOGLE, subject);
        return accountId;
    }

    private void persistLink(UUID accountId, ExternalProvider provider, String subject) {
        try {
            transactionTemplate.executeWithoutResult(status -> link(accountId, provider, subject));
        } catch (DataIntegrityViolationException _) {
            // Eşzamanlı iki hesap aynı harici kimliği bağlamaya çalıştı: veritabanı tekilliği birini reddeder.
            recordConflict(accountId);
            throw new AuthException(AuthErrorCode.EXTERNAL_IDENTITY_TAKEN);
        } catch (AuthException exception) {
            if (exception.errorCode() == AuthErrorCode.EXTERNAL_IDENTITY_TAKEN) {
                recordConflict(accountId);
            }
            throw exception;
        }
    }

    private void link(UUID accountId, ExternalProvider provider, String subject) {
        accountRepository.findByIdForUpdate(accountId).filter(Account::isActive)
                .orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID));
        Optional<ExternalIdentity> owner = identityRepository.findByProviderAndProviderSubject(provider, subject);
        if (owner.isPresent()) {
            if (owner.get().getAccountId().equals(accountId)) {
                return; // Aynı kimlik zaten bu hesapta: idempotent, ikinci olay yok.
            }
            throw new AuthException(AuthErrorCode.EXTERNAL_IDENTITY_TAKEN);
        }
        if (identityRepository.findByAccountIdAndProvider(accountId, provider).isPresent()) {
            throw new AuthException(AuthErrorCode.EXTERNAL_IDENTITY_ALREADY_LINKED);
        }
        identityRepository.saveAndFlush(ExternalIdentity.link(accountId, provider, subject, clock.instant()));
        securityEventRecorder.recordEvent(SecurityEventType.EXTERNAL_IDENTITY_LINKED, accountId);
        eventPublisher.publish(AuthEventType.EXTERNAL_IDENTITY_LINKED, accountId, provider.name() + ":" + subject);
    }

    /** Çakışma güvenlik olayı işlem dışında yazılır: işlem geri alınınca kaybolmaz. */
    private void recordConflict(UUID accountId) {
        securityEventRecorder.recordEvent(SecurityEventType.EXTERNAL_LINK_CONFLICT, accountId);
    }

    private UUID consumeFlow(String state, String stateFromCookie, ExternalProvider provider) {
        if (state == null || stateFromCookie == null || !MessageDigest.isEqual(
                state.getBytes(StandardCharsets.UTF_8), stateFromCookie.getBytes(StandardCharsets.UTF_8))) {
            throw failed();
        }
        String stateHash = secureTokens.sha256Hex(state);
        Optional<OAuthLinkFlow> flow = flowRepository.findById(stateHash);
        Integer consumed = transactionTemplate.execute(status ->
                flowRepository.consume(stateHash, provider, clock.instant()));
        if (flow.isEmpty() || consumed == null || consumed != 1) {
            throw failed();
        }
        return flow.get().getAccountId();
    }

    private Account activeAccount(UUID accountId) {
        Optional<Account> active = transactionTemplate.execute(status -> accountRepository.findById(accountId)
                .filter(Account::isActive));
        return active.orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID));
    }

    /** Parola (ve TOTP etkinse ikinci faktör) doğrulanır; denemeler hesap başına sınırlıdır. */
    private void reauthenticate(Account account, String password, String code, String recoveryCode) {
        UUID accountId = account.getId();
        String throttleKey = throttleHasher.hash(THROTTLE_DOMAIN, accountId.toString());
        attemptThrottle.reserve(throttleKey);
        String passwordHash = account.passwordHash().orElseThrow(() -> new AuthException(AuthErrorCode.PASSWORD_REQUIRED));
        if (password == null || !passwordEncoder.matches(password, passwordHash)) {
            securityEventRecorder.recordEvent(SecurityEventType.EXTERNAL_IDENTITY_LINK_REJECTED, accountId);
            throw new AuthException(AuthErrorCode.CURRENT_PASSWORD_INVALID);
        }
        if (twoFactorService.isEnabled(accountId)
                && !twoFactorService.verifySecondFactorForReauthentication(accountId, code, recoveryCode)) {
            securityEventRecorder.recordEvent(SecurityEventType.EXTERNAL_IDENTITY_LINK_REJECTED, accountId);
            throw new AuthException(AuthErrorCode.INVALID_TWO_FACTOR_CODE);
        }
        attemptThrottle.clear(throttleKey);
    }

    private String googleRedirectUri() {
        return urls.publicUrl(AuthApiPaths.GOOGLE_LINK_CALLBACK);
    }

    private String returnTo(String state) {
        return UriComponentsBuilder.fromUriString(urls.publicUrl(AuthApiPaths.STEAM_LINK_CALLBACK))
                .queryParam("state", state).build().toUriString();
    }

    private AuthException failed() {
        securityEventRecorder.recordEvent(SecurityEventType.EXTERNAL_LOGIN_FAILED, null);
        return new AuthException(AuthErrorCode.EXTERNAL_AUTH_FAILED);
    }
}
