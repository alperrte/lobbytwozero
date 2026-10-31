package com.ltz.auth.application.service;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.ltz.auth.api.controller.AuthApiPaths;
import com.ltz.auth.application.port.ExternalAuthenticationException;
import com.ltz.auth.application.port.SteamAssertionVerifier;
import com.ltz.auth.application.port.VerificationCodeMail;
import com.ltz.auth.application.service.SteamAssertionValidator.ValidAssertion;
import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.ExternalIdentity;
import com.ltz.auth.domain.entity.SteamRegistration;
import com.ltz.auth.domain.enums.AuthMethod;
import com.ltz.auth.domain.enums.ExternalProvider;
import com.ltz.auth.domain.enums.RoleCode;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.domain.enums.SteamProofPurpose;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.domain.exception.RedactedCause;
import com.ltz.auth.domain.exception.UsernameAlreadyTakenException;
import com.ltz.auth.infrastructure.config.OAuthProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.ExternalIdentityRepository;
import com.ltz.auth.infrastructure.persistence.repository.OpenIdNonceStore;
import com.ltz.auth.infrastructure.persistence.repository.RoleRepository;
import com.ltz.auth.infrastructure.persistence.repository.SteamRegistrationRepository;
import com.ltz.auth.infrastructure.security.SecureTokens;

import lombok.RequiredArgsConstructor;

/**
 * AUTH-05: Steam OpenID 2.0. Kimlik eşleştirmesi yalnız doğrulanmış SteamID64 ile yapılır; istemcinin beyan ettiği
 * hiçbir alan (kullanıcı adı, claimed_id metni) tek başına kimlik sayılmaz.
 * <ul>
 *   <li>Bağlı SteamID → hesap oturumu (durum, 2FA ve zorunlu rol kuralları parola girişiyle aynı).</li>
 *   <li>Bağsız SteamID → kısa ömürlü kayıt token'ı; e-posta toplanır, hesap PENDING_VERIFICATION açılır ve
 *       AUTH-02 e-posta doğrulamasıyla ACTIVE olur. Doğrulanmamış hesap oturum alamaz.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SteamLoginService {

    private static final String IDENTIFIER_SELECT = "http://specs.openid.net/auth/2.0/identifier_select";
    private static final String UNIQUE_USERNAME = "uq_accounts_username_normalized";

    private final OAuthProperties properties;
    private final OAuthFlowService flowService;
    private final SteamAssertionValidator validator;
    private final SteamAssertionVerifier verifier;
    private final OpenIdNonceStore nonceStore;
    private final ExternalUrls urls;
    private final AccountRepository accountRepository;
    private final ExternalIdentityRepository identityRepository;
    private final SteamRegistrationRepository registrationRepository;
    private final RoleRepository roleRepository;
    private final EmailVerificationService emailVerificationService;
    private final LoginService loginService;
    private final SecureTokens secureTokens;
    private final SecurityEventRecorder securityEventRecorder;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public StartedFlow start() {
        String state = flowService.start(ExternalProvider.STEAM);
        return new StartedFlow(authorizationUri(returnTo(state)), state);
    }

    /** Steam OpenID yönlendirme adresi; giriş ve bağlama akışları yalnız {@code returnTo} ile ayrılır. */
    URI authorizationUri(String returnTo) {
        return UriComponentsBuilder.fromUri(properties.steam().openIdEndpoint())
                .queryParam("openid.ns", SteamAssertionValidator.OPENID_NS)
                .queryParam("openid.mode", "checkid_setup")
                .queryParam("openid.return_to", returnTo)
                .queryParam("openid.realm", urls.realm())
                .queryParam("openid.identity", IDENTIFIER_SELECT)
                .queryParam("openid.claimed_id", IDENTIFIER_SELECT)
                .build().encode().toUri();
    }

    /** @param requestParams callback isteğinin tüm sorgu parametreleri (state + openid.*) */
    public ExternalLoginResult callback(Map<String, String[]> requestParams, String stateFromCookie) {
        String state = single(requestParams, "state");
        if (!flowService.consume(ExternalProvider.STEAM, state, stateFromCookie)) {
            throw failed();
        }
        String steamId = verifiedSteamId(requestParams, returnTo(state));
        Optional<ExternalIdentity> linked = identityRepository.findByProviderAndProviderSubject(
                ExternalProvider.STEAM, steamId);
        if (linked.isPresent()) {
            // E-postası henüz doğrulanmamış Steam hesabı oturum almaz; doğrulamayı yapmak için SteamID kanıtı verilir.
            if (accountRepository.findById(linked.get().getAccountId())
                    .filter(Account::isPendingVerification).isPresent()) {
                return ExternalLoginResult.emailVerificationRequired(issueVerificationProofInNewTransaction(steamId));
            }
            return ExternalLoginResult.from(
                    loginService.loginVerifiedIdentity(linked.get().getAccountId(), AuthMethod.EXTERNAL));
        }
        return ExternalLoginResult.registrationRequired(issueRegistrationToken(steamId));
    }

    /**
     * Steam yanıtını doğrular ve doğrulanmış SteamID64'ü verir: imza/alan denetimi, replay (nonce) ve Steam'e geri
     * sorgu. Herhangi bir hata aynı {@code EXTERNAL_AUTH_FAILED} olur. Giriş ve bağlama akışları aynı doğrulamayı kullanır.
     */
    String verifiedSteamId(Map<String, String[]> requestParams, String returnTo) {
        try {
            Instant now = clock.instant();
            ValidAssertion assertion = validator.validate(requestParams, returnTo, now);
            // Replay: nonce Steam'e sorulmadan önce atomik olarak tüketilir; ikinci kullanım burada düşer.
            if (!nonceStore.firstUse(assertion.responseNonce(), now)) {
                throw new ExternalAuthenticationException("Steam response_nonce already used");
            }
            if (!verifier.isValid(assertion.openIdParams())) {
                throw new ExternalAuthenticationException("Steam rejected the assertion");
            }
            return assertion.steamId();
        } catch (ExternalAuthenticationException _) {
            throw failed();
        }
    }

    /**
     * Steam ile yeni hesap: e-posta ve kullanıcı adı toplanır. Kayıtlı e-postada hesap açılmaz ve yanıt değişmez
     * (numaralandırma yok); kayıt hakkı her iki durumda da tüketilir. Kullanıcı adı çakışması 409.
     *
     * @return e-posta doğrulamasında kayıt sahibini kanıtlayan tek kullanımlık Steam kanıtı (verificationProof).
     *         Her durumda aynı biçimde bir kanıt döner (kayıtlı e-posta dahil); yanıt hesap varlığını sızdırmaz.
     */
    public String completeRegistration(String registrationToken, String email, String username) {
        Completion completion = createPending(registrationToken, email, username);
        if (completion.mail() != null) {
            emailVerificationService.dispatch(completion.mail());
        }
        return completion.proof();
    }

    private Completion createPending(String registrationToken, String email, String username) {
        try {
            return transactionTemplate.execute(status -> {
                Instant now = clock.instant();
                SteamRegistration registration = registrationRepository
                        .findByTokenHashForUpdate(secureTokens.sha256Hex(registrationToken))
                        .filter(candidate -> candidate.isUsableAt(now, SteamProofPurpose.REGISTRATION))
                        .orElseThrow(() -> new AuthException(AuthErrorCode.REGISTRATION_TOKEN_INVALID));
                if (accountRepository.existsByUsernameNormalized(Account.normalizeUsername(username))) {
                    throw new UsernameAlreadyTakenException();
                }
                registration.consume(now);
                registrationRepository.flush();
                String proof = issueVerificationProof(registration.getSteamId(), now);
                if (accountRepository.findByEmailNormalized(Account.normalizeEmail(email)).isPresent()) {
                    securityEventRecorder.recordEvent(SecurityEventType.REGISTRATION_EMAIL_CONFLICT, null);
                    return new Completion(null, proof);
                }
                var role = roleRepository.findByCode(RoleCode.PLAYER).orElseThrow(
                        () -> new IllegalStateException("Initial role seed is missing: " + RoleCode.PLAYER));
                Account account = accountRepository.saveAndFlush(
                        Account.registerPending(email, username, null, role, now));
                identityRepository.saveAndFlush(
                        ExternalIdentity.link(account.getId(), ExternalProvider.STEAM, registration.getSteamId(), now));
                securityEventRecorder.recordEvent(SecurityEventType.ACCOUNT_REGISTERED, account.getId());
                return new Completion(emailVerificationService.issueCode(account, now), proof);
            });
        } catch (DataIntegrityViolationException exception) {
            String constraint = violatedConstraint(exception);
            if (UNIQUE_USERNAME.equalsIgnoreCase(constraint)) {
                throw new UsernameAlreadyTakenException(RedactedCause.of(exception));
            }
            if (constraint != null) {
                // E-posta veya SteamID eşzamanlı olarak alındı: nötr yanıt, hesap açılmaz; kanıt hiçbir kayda bağlı değildir.
                securityEventRecorder.recordEvent(SecurityEventType.EXTERNAL_LINK_CONFLICT, null);
                return new Completion(null, secureTokens.newOpaqueToken());
            }
            throw exception;
        }
    }

    private String issueRegistrationToken(String steamId) {
        String token = secureTokens.newOpaqueToken();
        transactionTemplate.executeWithoutResult(status -> {
            Instant now = clock.instant();
            registrationRepository.save(SteamRegistration.issue(secureTokens.sha256Hex(token), steamId,
                    SteamProofPurpose.REGISTRATION, now, now.plus(properties.steam().registrationTtl())));
            securityEventRecorder.recordEvent(SecurityEventType.STEAM_REGISTRATION_STARTED, null);
        });
        return token;
    }

    /** Çağıranın işlemine katılır; e-posta doğrulamasında kayıt sahibinin SteamID'yi kontrol ettiğini kanıtlar. */
    private String issueVerificationProof(String steamId, Instant now) {
        String proof = secureTokens.newOpaqueToken();
        registrationRepository.save(SteamRegistration.issue(secureTokens.sha256Hex(proof), steamId,
                SteamProofPurpose.EMAIL_VERIFICATION, now, now.plus(properties.steam().verificationProofTtl())));
        return proof;
    }

    private String issueVerificationProofInNewTransaction(String steamId) {
        return transactionTemplate.execute(status -> issueVerificationProof(steamId, clock.instant()));
    }

    private record Completion(VerificationCodeMail mail, String proof) {

        @Override
        public String toString() {
            return "Completion[mail=***, proof=***]";
        }
    }

    private String returnTo(String state) {
        return UriComponentsBuilder.fromUriString(urls.publicUrl(AuthApiPaths.STEAM_CALLBACK))
                .queryParam("state", state).build().toUriString();
    }

    private AuthException failed() {
        securityEventRecorder.recordEvent(SecurityEventType.EXTERNAL_LOGIN_FAILED, null);
        return new AuthException(AuthErrorCode.EXTERNAL_AUTH_FAILED);
    }

    private static String single(Map<String, String[]> params, String name) {
        String[] values = params.get(name);
        return values != null && values.length == 1 ? values[0] : null;
    }

    private static String violatedConstraint(DataIntegrityViolationException exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }
}
