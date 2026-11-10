package com.ltz.auth.application.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.ltz.auth.api.controller.AuthApiPaths;
import com.ltz.auth.application.port.ExternalAuthenticationException;
import com.ltz.auth.application.port.GoogleOidcPort;
import com.ltz.auth.application.port.GoogleOidcPort.GoogleIdentity;
import com.ltz.auth.application.port.SecurityNotificationType;
import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.ExternalIdentity;
import com.ltz.auth.domain.entity.GoogleRegistration;
import com.ltz.auth.domain.enums.AuthEventType;
import com.ltz.auth.domain.enums.AuthMethod;
import com.ltz.auth.domain.enums.ExternalProvider;
import com.ltz.auth.domain.enums.RoleCode;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.domain.exception.RedactedCause;
import com.ltz.auth.domain.exception.UsernameAlreadyTakenException;
import com.ltz.auth.infrastructure.config.OAuthProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.ExternalIdentityRepository;
import com.ltz.auth.infrastructure.persistence.repository.GoogleRegistrationRepository;
import com.ltz.auth.infrastructure.persistence.repository.RoleRepository;
import com.ltz.auth.infrastructure.security.SecureTokens;

import lombok.RequiredArgsConstructor;

/**
 * AUTH-06: Google OIDC (Authorization Code + PKCE S256). Kimlik eşleştirmesi yalnız Google "sub" ile yapılır.
 * <ul>
 *   <li>Bağlı sub → hesap oturumu (durum, 2FA ve zorunlu rol kuralları parola girişiyle aynı).</li>
 *   <li>Bağsız sub + doğrulanmış e-posta + kayıtsız e-posta → hesap açılmaz; kullanıcı adı seçimi için kayıt token'ı
 *       döner, hesap (PLAYER, parolasız, ACTIVE) kullanıcı adı seçilince açılır.</li>
 *   <li>E-posta zaten kayıtlıysa sessizce birleştirilmez; ACCOUNT_LINK_REQUIRED döner.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class GoogleLoginService {

    private static final String SCOPE = "openid email";
    private static final String UNIQUE_USERNAME = "uq_accounts_username_normalized";

    private final OAuthProperties properties;
    private final OAuthFlowService flowService;
    private final GoogleOidcPort googleOidc;
    private final ExternalUrls urls;
    private final AccountRepository accountRepository;
    private final ExternalIdentityRepository identityRepository;
    private final RoleRepository roleRepository;
    private final GoogleRegistrationRepository registrationRepository;
    private final SecureTokens secureTokens;
    private final LoginService loginService;
    private final SecurityEventRecorder securityEventRecorder;
    private final AuthEventPublisher eventPublisher;
    private final SecurityNotificationDispatcher notifier;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public StartedFlow start() {
        requireConfigured();
        String state = flowService.start(ExternalProvider.GOOGLE);
        return new StartedFlow(authorizationUri(state, redirectUri()), state);
    }

    /**
     * Verilen state için Google yetkilendirme adresi. Giriş ve bağlama akışları paylaşır; PKCE doğrulayıcısı ve nonce
     * state'ten türetilir, saklanmaz.
     */
    URI authorizationUri(String state, String redirectUri) {
        requireConfigured();
        var google = properties.google();
        return UriComponentsBuilder.fromUri(google.authorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", google.clientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", SCOPE)
                .queryParam("state", state)
                .queryParam("nonce", flowService.nonce(state))
                .queryParam("code_challenge", codeChallenge(flowService.codeVerifier(state)))
                .queryParam("code_challenge_method", "S256")
                .queryParam("prompt", "select_account")
                .build().encode().toUri();
    }

    /** Kodu Google'da doğrular ve kimliği döner; hesap eşleştirmesi çağırana aittir. */
    GoogleIdentity verifiedIdentity(String code, String state, String redirectUri) {
        requireConfigured();
        if (code == null || code.isBlank()) {
            throw failed();
        }
        try {
            return googleOidc.authenticate(code, flowService.codeVerifier(state), redirectUri, flowService.nonce(state));
        } catch (ExternalAuthenticationException _) {
            throw failed();
        }
    }

    /**
     * Bağlı kimlik: oturum akışı. Bağsız kimlik + doğrulanmış, kayıtsız e-posta: hesap AÇILMAZ; kullanıcı adı seçsin diye
     * kısa ömürlü, tek kullanımlık kayıt token'ı döner ({@link #completeRegistration}). E-posta ve "sub" sunucuda kalır.
     */
    public ExternalLoginResult callback(String code, String state, String stateFromCookie) {
        requireConfigured();
        if (code == null || code.isBlank() || !flowService.consume(ExternalProvider.GOOGLE, state, stateFromCookie)) {
            throw failed();
        }
        GoogleIdentity identity = verifiedIdentity(code, state, redirectUri());
        Optional<ExternalIdentity> linked = identityRepository.findByProviderAndProviderSubject(
                ExternalProvider.GOOGLE, identity.subject());
        if (linked.isPresent()) {
            return ExternalLoginResult.from(
                    loginService.loginVerifiedIdentity(linked.get().getAccountId(), AuthMethod.EXTERNAL));
        }
        requireRegistrable(identity);
        return ExternalLoginResult.googleRegistrationRequired(issueRegistrationToken(identity));
    }

    /**
     * Kullanıcı adıyla ilk Google kaydını tamamlar: token tek kullanımlık ve süreli; hesap doğrulanmış e-postayla ACTIVE
     * açılır, "sub" bağlanır. Sonra parola girişiyle aynı kurallarla oturum/2FA akışı çalışır (MFA atlanmaz).
     * Kullanıcı adı çakışmasında token tüketilmez (başka ad denenebilir). Eşzamanlı kullanımda ikincisi token'ı tüketilmiş görür.
     */
    public LoginResult completeRegistration(String registrationToken, String username) {
        UUID accountId = createAccountFromRegistration(registrationToken, username);
        return loginService.loginVerifiedIdentity(accountId, AuthMethod.EXTERNAL);
    }

    private void requireRegistrable(GoogleIdentity identity) {
        if (!identity.emailVerified() || identity.email() == null || identity.email().isBlank()) {
            securityEventRecorder.recordEvent(SecurityEventType.EXTERNAL_LOGIN_FAILED, null);
            throw new AuthException(AuthErrorCode.EXTERNAL_EMAIL_UNVERIFIED);
        }
        Optional<Account> sameEmail = accountRepository.findByEmailNormalized(Account.normalizeEmail(identity.email()));
        if (sameEmail.isPresent()) {
            securityEventRecorder.recordEvent(SecurityEventType.EXTERNAL_LINK_CONFLICT, sameEmail.get().getId());
            throw new AuthException(AuthErrorCode.ACCOUNT_LINK_REQUIRED);
        }
    }

    private String issueRegistrationToken(GoogleIdentity identity) {
        String token = secureTokens.newOpaqueToken();
        transactionTemplate.executeWithoutResult(status -> {
            Instant now = clock.instant();
            registrationRepository.save(GoogleRegistration.issue(secureTokens.sha256Hex(token), identity.subject(),
                    identity.email().strip(), now, now.plus(properties.google().registrationTtl())));
            securityEventRecorder.recordEvent(SecurityEventType.GOOGLE_REGISTRATION_STARTED, null);
        });
        return token;
    }

    private UUID createAccountFromRegistration(String registrationToken, String username) {
        String tokenHash = secureTokens.sha256Hex(registrationToken);
        try {
            return transactionTemplate.execute(status -> {
                Instant now = clock.instant();
                GoogleRegistration registration = registrationRepository.findByTokenHashForUpdate(tokenHash)
                        .filter(candidate -> candidate.isUsableAt(now))
                        .orElseThrow(() -> new AuthException(AuthErrorCode.REGISTRATION_TOKEN_INVALID));
                if (accountRepository.existsByUsernameNormalized(Account.normalizeUsername(username))) {
                    throw new UsernameAlreadyTakenException();
                }
                Optional<ExternalIdentity> linked = identityRepository.findByProviderAndProviderSubject(
                        ExternalProvider.GOOGLE, registration.getSubject());
                if (linked.isPresent()) {
                    // Aynı Google kimliği bu arada başka bir akışla kayıt oldu: ikinci hesap açılmaz, mevcut hesapla devam.
                    registration.consume(now);
                    return linked.get().getAccountId();
                }
                if (accountRepository.findByEmailNormalized(Account.normalizeEmail(registration.getEmail())).isPresent()) {
                    throw new AuthException(AuthErrorCode.ACCOUNT_LINK_REQUIRED);
                }
                var role = roleRepository.findByCode(RoleCode.PLAYER)
                        .orElseThrow(() -> new IllegalStateException("Initial role seed is missing: " + RoleCode.PLAYER));
                Account account = accountRepository.saveAndFlush(
                        Account.registerVerifiedExternal(registration.getEmail(), username, role, now));
                identityRepository.saveAndFlush(ExternalIdentity.link(account.getId(), ExternalProvider.GOOGLE,
                        registration.getSubject(), now));
                registration.consume(now);
                securityEventRecorder.recordEvent(SecurityEventType.EXTERNAL_ACCOUNT_CREATED, account.getId());
                eventPublisher.publish(AuthEventType.USER_REGISTERED, account.getId(), account.getUsername());
                // Hesap ilk kez Google'ın doğruladığı adresle açıldı: commit sonrası, en iyi çaba, bir kez.
                notifier.notifyOnceAfterCommit(account.getId(), account.getEmail(), SecurityNotificationType.WELCOME);
                return account.getId();
            });
        } catch (DataIntegrityViolationException exception) {
            if (UNIQUE_USERNAME.equalsIgnoreCase(violatedConstraint(exception))) {
                throw new UsernameAlreadyTakenException(RedactedCause.of(exception));
            }
            // E-posta ya da Google kimliği eşzamanlı olarak alındı: birleştirme yok, kullanıcı mevcut hesapla girmeli.
            throw new AuthException(AuthErrorCode.ACCOUNT_LINK_REQUIRED, RedactedCause.of(exception));
        }
    }

    private static String violatedConstraint(DataIntegrityViolationException exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
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
