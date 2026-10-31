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
 * Oturumdaki hesaba Steam bağlama ve kaldırma (AUTH-20). Kimlik kanıtı yalnız Auth'ta üretilir: SteamID64 giriş akışıyla
 * aynı OpenID doğrulamasından ({@link SteamLoginService#verifiedSteamId}) geçer.
 * <ul>
 *   <li><b>Yeniden doğrulama:</b> bağlama ve kaldırma, giriş yöntemini değiştirir. Cihaz bağlama ve e-posta
 *       değiştirmedeki gibi mevcut parola (TOTP etkinse ikinci faktör) istenir; çalınmış bir oturum kendi Steam'ini
 *       bağlayıp hesabı sonradan ele geçiremez. Parolasız hesap bu işlemi yapamaz ({@code PASSWORD_REQUIRED}).</li>
 *   <li><b>Hesaba bağlı akış:</b> hangi hesaba bağlanacağı akışa yazılır ({@code oauth_link_flows}); state ayrıca tarayıcı
 *       çerezine bağlıdır. Başkasının başlattığı akış, kurbanın tarayıcısında tamamlanamaz.</li>
 *   <li><b>Tekillik:</b> bir Steam kimliği tek hesaba, bir hesap tek Steam kimliğine bağlıdır (veritabanı da zorlar).</li>
 *   <li><b>Son yöntem koruması:</b> Steam, hesabın tek giriş yöntemiyse kaldırılamaz ({@code LAST_LOGIN_METHOD}).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ExternalIdentityLinkService {

    private static final String THROTTLE_DOMAIN = "EXTERNAL_IDENTITY_LINK";
    private static final String STEAM_VALUE_PREFIX = "STEAM:";

    private final SteamLoginService steamLoginService;
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

    /** Yeniden doğrulamadan sonra bağlama akışını başlatır; tarayıcı dönen adrese yönlendirilir. */
    public StartedFlow startSteamLink(UUID accountId, String password, String code, String recoveryCode) {
        Account account = activeAccount(accountId);
        reauthenticate(account, password, code, recoveryCode);
        if (identityRepository.findByAccountIdAndProvider(accountId, ExternalProvider.STEAM).isPresent()) {
            throw new AuthException(AuthErrorCode.EXTERNAL_IDENTITY_ALREADY_LINKED);
        }
        String state = transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            String newState = secureTokens.newOpaqueToken();
            flowRepository.save(OAuthLinkFlow.start(secureTokens.sha256Hex(newState), accountId, ExternalProvider.STEAM,
                    now, now.plus(properties.flowTtl())));
            return newState;
        });
        return new StartedFlow(steamLoginService.authorizationUri(returnTo(state)), state);
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
        UUID accountId = consumeFlow(state, stateFromCookie);
        String steamId = steamLoginService.verifiedSteamId(requestParams, returnTo(state));
        try {
            transactionTemplate.executeWithoutResult(status -> link(accountId, steamId));
        } catch (DataIntegrityViolationException _) {
            // Eşzamanlı iki hesap aynı Steam kimliğini bağlamaya çalıştı: veritabanı tekilliği birini reddeder.
            recordConflict(accountId);
            throw new AuthException(AuthErrorCode.EXTERNAL_IDENTITY_TAKEN);
        } catch (AuthException exception) {
            if (exception.errorCode() == AuthErrorCode.EXTERNAL_IDENTITY_TAKEN) {
                recordConflict(accountId);
            }
            throw exception;
        }
        return accountId;
    }

    /** Steam bağlantısını kaldırır. Hesabın tek giriş yöntemiyse kaldırma reddedilir. */
    public void unlinkSteam(UUID accountId, String password, String code, String recoveryCode) {
        Account account = activeAccount(accountId);
        if (account.passwordHash().isEmpty() && !identityRepository.existsByAccountIdAndProviderNot(accountId,
                ExternalProvider.STEAM)) {
            throw new AuthException(AuthErrorCode.LAST_LOGIN_METHOD);
        }
        reauthenticate(account, password, code, recoveryCode);
        transactionTemplate.executeWithoutResult(status -> {
            ExternalIdentity identity = identityRepository.findByAccountIdAndProvider(accountId, ExternalProvider.STEAM)
                    .orElseThrow(() -> new AuthException(AuthErrorCode.EXTERNAL_IDENTITY_NOT_LINKED));
            identityRepository.delete(identity);
            securityEventRecorder.recordEvent(SecurityEventType.EXTERNAL_IDENTITY_UNLINKED, accountId);
            eventPublisher.publish(AuthEventType.EXTERNAL_IDENTITY_UNLINKED, accountId, ExternalProvider.STEAM.name());
        });
    }

    private void link(UUID accountId, String steamId) {
        accountRepository.findByIdForUpdate(accountId).filter(Account::isActive)
                .orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID));
        Optional<ExternalIdentity> owner = identityRepository.findByProviderAndProviderSubject(ExternalProvider.STEAM, steamId);
        if (owner.isPresent()) {
            if (owner.get().getAccountId().equals(accountId)) {
                return; // Aynı kimlik zaten bu hesapta: idempotent, ikinci olay yok.
            }
            throw new AuthException(AuthErrorCode.EXTERNAL_IDENTITY_TAKEN);
        }
        if (identityRepository.findByAccountIdAndProvider(accountId, ExternalProvider.STEAM).isPresent()) {
            throw new AuthException(AuthErrorCode.EXTERNAL_IDENTITY_ALREADY_LINKED);
        }
        identityRepository.saveAndFlush(ExternalIdentity.link(accountId, ExternalProvider.STEAM, steamId, clock.instant()));
        securityEventRecorder.recordEvent(SecurityEventType.EXTERNAL_IDENTITY_LINKED, accountId);
        eventPublisher.publish(AuthEventType.EXTERNAL_IDENTITY_LINKED, accountId, STEAM_VALUE_PREFIX + steamId);
    }

    /** Çakışma güvenlik olayı işlem dışında yazılır: işlem geri alınınca kaybolmaz. */
    private void recordConflict(UUID accountId) {
        securityEventRecorder.recordEvent(SecurityEventType.EXTERNAL_LINK_CONFLICT, accountId);
    }

    private UUID consumeFlow(String state, String stateFromCookie) {
        if (state == null || stateFromCookie == null || !MessageDigest.isEqual(
                state.getBytes(StandardCharsets.UTF_8), stateFromCookie.getBytes(StandardCharsets.UTF_8))) {
            throw failed();
        }
        String stateHash = secureTokens.sha256Hex(state);
        Optional<OAuthLinkFlow> flow = flowRepository.findById(stateHash);
        Integer consumed = transactionTemplate.execute(status ->
                flowRepository.consume(stateHash, ExternalProvider.STEAM, clock.instant()));
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

    private String returnTo(String state) {
        return UriComponentsBuilder.fromUriString(urls.publicUrl(AuthApiPaths.STEAM_LINK_CALLBACK))
                .queryParam("state", state).build().toUriString();
    }

    private AuthException failed() {
        securityEventRecorder.recordEvent(SecurityEventType.EXTERNAL_LOGIN_FAILED, null);
        return new AuthException(AuthErrorCode.EXTERNAL_AUTH_FAILED);
    }
}
