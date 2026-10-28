package com.ltz.auth.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.ltz.auth.api.controller.AuthApiPaths;
import com.ltz.auth.application.port.ExternalAuthenticationException;
import com.ltz.auth.application.port.GoogleOidcPort;
import com.ltz.auth.application.port.GoogleOidcPort.GoogleIdentity;
import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.ExternalIdentity;
import com.ltz.auth.domain.enums.AuthEventType;
import com.ltz.auth.domain.enums.AuthMethod;
import com.ltz.auth.domain.enums.ExternalProvider;
import com.ltz.auth.domain.enums.RoleCode;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.config.OAuthProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.ExternalIdentityRepository;
import com.ltz.auth.infrastructure.persistence.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * AUTH-06: Google OIDC (Authorization Code + PKCE S256). Kimlik eşleştirmesi yalnız Google "sub" ile yapılır.
 * <ul>
 *   <li>Bağlı sub → hesap oturumu (durum, 2FA ve zorunlu rol kuralları parola girişiyle aynı).</li>
 *   <li>Bağsız sub + doğrulanmış e-posta + kayıtsız e-posta → yeni ACTIVE hesap (PLAYER, parolasız).</li>
 *   <li>E-posta zaten kayıtlıysa sessizce birleştirilmez; ACCOUNT_LINK_REQUIRED döner.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class GoogleLoginService {

    private static final String SCOPE = "openid email";
    private static final String USERNAME_PREFIX = "g_";
    private static final int USERNAME_SUFFIX_LENGTH = 12;
    private static final int USERNAME_ATTEMPTS = 5;

    private final OAuthProperties properties;
    private final OAuthFlowService flowService;
    private final GoogleOidcPort googleOidc;
    private final ExternalUrls urls;
    private final AccountRepository accountRepository;
    private final ExternalIdentityRepository identityRepository;
    private final RoleRepository roleRepository;
    private final LoginService loginService;
    private final SecurityEventRecorder securityEventRecorder;
    private final AuthEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public StartedFlow start() {
        requireConfigured();
        String state = flowService.start(ExternalProvider.GOOGLE);
        var google = properties.google();
        var redirect = UriComponentsBuilder.fromUri(google.authorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", google.clientId())
                .queryParam("redirect_uri", redirectUri())
                .queryParam("scope", SCOPE)
                .queryParam("state", state)
                .queryParam("nonce", flowService.nonce(state))
                .queryParam("code_challenge", codeChallenge(flowService.codeVerifier(state)))
                .queryParam("code_challenge_method", "S256")
                .queryParam("prompt", "select_account")
                .build().encode().toUri();
        return new StartedFlow(redirect, state);
    }

    public ExternalLoginResult callback(String code, String state, String stateFromCookie) {
        requireConfigured();
        if (code == null || code.isBlank() || !flowService.consume(ExternalProvider.GOOGLE, state, stateFromCookie)) {
            throw failed();
        }
        GoogleIdentity identity;
        try {
            identity = googleOidc.authenticate(code, flowService.codeVerifier(state), redirectUri(),
                    flowService.nonce(state));
        } catch (ExternalAuthenticationException _) {
            throw failed();
        }
        UUID accountId = resolveAccount(identity);
        return ExternalLoginResult.from(loginService.loginVerifiedIdentity(accountId, AuthMethod.EXTERNAL));
    }

    private UUID resolveAccount(GoogleIdentity identity) {
        Optional<ExternalIdentity> linked = identityRepository.findByProviderAndProviderSubject(
                ExternalProvider.GOOGLE, identity.subject());
        if (linked.isPresent()) {
            return linked.get().getAccountId();
        }
        if (!identity.emailVerified() || identity.email() == null || identity.email().isBlank()) {
            securityEventRecorder.recordEvent(SecurityEventType.EXTERNAL_LOGIN_FAILED, null);
            throw new AuthException(AuthErrorCode.EXTERNAL_EMAIL_UNVERIFIED);
        }
        Optional<Account> sameEmail = accountRepository.findByEmailNormalized(Account.normalizeEmail(identity.email()));
        if (sameEmail.isPresent()) {
            securityEventRecorder.recordEvent(SecurityEventType.EXTERNAL_LINK_CONFLICT, sameEmail.get().getId());
            throw new AuthException(AuthErrorCode.ACCOUNT_LINK_REQUIRED);
        }
        try {
            return transactionTemplate.execute(status -> createAccount(identity));
        } catch (DataIntegrityViolationException _) {
            // Eşzamanlı ilk giriş veya aynı e-postayla eşzamanlı kayıt: ikisi de bağsız birleştirmeye izin vermez.
            return identityRepository.findByProviderAndProviderSubject(ExternalProvider.GOOGLE, identity.subject())
                    .map(ExternalIdentity::getAccountId)
                    .orElseThrow(() -> new AuthException(AuthErrorCode.ACCOUNT_LINK_REQUIRED));
        }
    }

    private UUID createAccount(GoogleIdentity identity) {
        Instant now = clock.instant();
        var role = roleRepository.findByCode(RoleCode.PLAYER)
                .orElseThrow(() -> new IllegalStateException("Initial role seed is missing: " + RoleCode.PLAYER));
        Account account = accountRepository.saveAndFlush(
                Account.registerVerifiedExternal(identity.email(), freeUsername(), role, now));
        identityRepository.saveAndFlush(ExternalIdentity.link(account.getId(), ExternalProvider.GOOGLE,
                identity.subject(), now));
        securityEventRecorder.recordEvent(SecurityEventType.EXTERNAL_ACCOUNT_CREATED, account.getId());
        eventPublisher.publish(AuthEventType.USER_REGISTERED, account.getId(), account.getUsername());
        return account.getId();
    }

    private String freeUsername() {
        for (int attempt = 0; attempt < USERNAME_ATTEMPTS; attempt++) {
            String candidate = USERNAME_PREFIX + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, USERNAME_SUFFIX_LENGTH);
            if (!accountRepository.existsByUsernameNormalized(Account.normalizeUsername(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a username");
    }

    private AuthException failed() {
        securityEventRecorder.recordEvent(SecurityEventType.EXTERNAL_LOGIN_FAILED, null);
        return new AuthException(AuthErrorCode.EXTERNAL_AUTH_FAILED);
    }

    private void requireConfigured() {
        if (!properties.google().isConfigured()) {
            throw new AuthException(AuthErrorCode.PROVIDER_NOT_CONFIGURED);
        }
    }

    private String redirectUri() {
        return urls.publicUrl(AuthApiPaths.GOOGLE_CALLBACK);
    }

    /** RFC 7636 S256: BASE64URL(SHA256(verifier)). */
    private static String codeChallenge(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
