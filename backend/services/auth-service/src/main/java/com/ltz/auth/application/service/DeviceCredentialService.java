package com.ltz.auth.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.DeviceCredential;
import com.ltz.auth.domain.enums.DeviceRevokeReason;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.config.DeviceProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.DeviceCredentialRepository;
import com.ltz.auth.infrastructure.security.DeviceTokenIssuer;
import com.ltz.auth.infrastructure.security.DeviceTokenTarget;
import com.ltz.auth.infrastructure.security.SecureTokens;
import com.ltz.auth.infrastructure.security.VerificationCodeHasher;

import lombok.RequiredArgsConstructor;

/**
 * Masaüstü cihaz kimliği (AUTH-17, B yolu).
 * <ul>
 *   <li><b>Bağlama:</b> yeniden kimlik doğrulama (parola; TOTP etkinse ikinci faktör). Gizli anahtar 256 bit rastgele,
 *       yalnız bağlama yanıtında bir kez görünür, saklanan yalnız SHA-256 özetidir.</li>
 *   <li><b>Token:</b> cihaz kimliği ve anahtarıyla kısa ömürlü {@code dev+jwt}. Yanlış, bilinmeyen, iptal edilmiş, süresi
 *       dolmuş cihaz ve aktif olmayan hesap aynı hatayı verir; bilinmeyen cihazda da aynı maliyetli karşılaştırma yapılır.
 *       Hesap kilidi yoktur (gerçek cihazı dışarıdan kilitlemek mümkün olmasın): 256 bit anahtar tahmin edilemez, IP başına
 *       hız sınırını Gateway uygular.</li>
 *   <li><b>İptal:</b> kullanıcı iptali ve hesap güvenliği değişimleri (parola, e-posta, askı, silme) cihazı kapatır; yeni
 *       token alınamaz, eldeki token en çok {@link DeviceTokenIssuer#TTL} yaşar.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DeviceCredentialService {

    static final String THROTTLE_DOMAIN = "DEVICE_BIND";
    private static final Logger log = LoggerFactory.getLogger(DeviceCredentialService.class);
    private static final Duration DAILY_WINDOW = Duration.ofDays(1);
    private static final Duration LAST_USED_RESOLUTION = Duration.ofMinutes(5);
    private static final int DIGEST_LENGTH = 64;
    /** Her çağrıda yeni örnek yerine tek örnek (statik başlatma sırası için {@link #DUMMY_DIGEST}'ten önce tanımlı). */
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Bilinmeyen cihazda karşılaştırılan rastgele değer (yanıt süresi farkını azaltır). */
    private static final byte[] DUMMY_DIGEST = randomBytes();

    private final AccountRepository accountRepository;
    private final DeviceCredentialRepository deviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final TwoFactorService twoFactorService;
    private final AttemptThrottleService attemptThrottle;
    private final VerificationCodeHasher throttleHasher;
    private final SecureTokens secureTokens;
    private final DeviceTokenIssuer tokenIssuer;
    private final SecurityEventRecorder securityEventRecorder;
    private final DeviceProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public BoundDevice bind(UUID accountId, String label, String password, String code, String recoveryCode) {
        String throttleKey = throttleHasher.hash(THROTTLE_DOMAIN, accountId.toString());
        attemptThrottle.reserve(throttleKey);
        Optional<Account> activeAccount = transactionTemplate.execute(status -> accountRepository.findById(accountId)
                .filter(Account::isActive));
        Account account = activeAccount.orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID));
        String passwordHash = account.passwordHash().orElseThrow(() -> new AuthException(AuthErrorCode.PASSWORD_REQUIRED));
        if (password == null || !passwordEncoder.matches(password, passwordHash)) {
            securityEventRecorder.recordEvent(SecurityEventType.DEVICE_BIND_REJECTED, accountId);
            throw new AuthException(AuthErrorCode.CURRENT_PASSWORD_INVALID);
        }
        if (twoFactorService.isEnabled(accountId)
                && !twoFactorService.verifySecondFactorForReauthentication(accountId, code, recoveryCode)) {
            securityEventRecorder.recordEvent(SecurityEventType.DEVICE_BIND_REJECTED, accountId);
            throw new AuthException(AuthErrorCode.INVALID_TWO_FACTOR_CODE);
        }
        attemptThrottle.clear(throttleKey);
        return transactionTemplate.execute(status -> persist(accountId, label.strip()));
    }

    /** Hesabın kullanılabilir (aktif ve süresi dolmamış) cihazları; gizli anahtar hiçbir yerde dönmez. */
    public List<DeviceCredential> list(UUID accountId) {
        return transactionTemplate.execute(status -> deviceRepository.findUsable(accountId, clock.instant()));
    }

    /** Yalnız kendi cihazı iptal edilir; başkasının veya olmayan cihaz ayırt edilmeden 404. İptal idempotenttir. */
    public void revoke(UUID accountId, UUID deviceId) {
        transactionTemplate.executeWithoutResult(status -> {
            DeviceCredential device = deviceRepository.findByIdAndAccountId(deviceId, accountId)
                    .orElseThrow(() -> new AuthException(AuthErrorCode.DEVICE_NOT_FOUND));
            if (device.revoke(DeviceRevokeReason.USER, clock.instant())) {
                securityEventRecorder.recordEvent(SecurityEventType.DEVICE_REVOKED, accountId);
            }
        });
    }

    public IssuedDeviceToken issueToken(UUID deviceId, String deviceSecret) {
        return issueToken(deviceId, deviceSecret, DeviceTokenTarget.HARDWARE);
    }

    /** Hedef ne olursa olsun kimlik doğrulaması ve iptal kuralları aynıdır; hata yanıtı hedefi sızdırmaz. */
    public IssuedDeviceToken issueToken(UUID deviceId, String deviceSecret, DeviceTokenTarget target) {
        Optional<IssuedDeviceToken> issued = transactionTemplate.execute(status -> tryIssue(deviceId, deviceSecret, target));
        if (issued.isEmpty()) {
            log.warn("Device authentication failed deviceId={}", deviceId);
            throw new AuthException(AuthErrorCode.INVALID_DEVICE_CREDENTIALS);
        }
        return issued.get();
    }

    private BoundDevice persist(UUID accountId, String label) {
        // Hesap satırı kilidi: eşzamanlı bağlamalar sınırı birlikte aşamaz.
        Account account = accountRepository.findByIdForUpdate(accountId).filter(Account::isActive)
                .orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID));
        Instant now = clock.instant();
        if (deviceRepository.countUsable(account.getId(), now) >= properties.maxActive()) {
            throw new AuthException(AuthErrorCode.DEVICE_LIMIT_REACHED);
        }
        if (deviceRepository.countByAccountIdAndCreatedAtAfter(account.getId(), now.minus(DAILY_WINDOW))
                >= properties.dailyBindLimit()) {
            throw new AuthException(AuthErrorCode.TOO_MANY_ATTEMPTS, DAILY_WINDOW);
        }
        String secret = secureTokens.newOpaqueToken();
        DeviceCredential device = deviceRepository.save(DeviceCredential.bind(account.getId(), label,
                secureTokens.sha256Hex(secret), now, properties.credentialTtl()));
        securityEventRecorder.recordEvent(SecurityEventType.DEVICE_BOUND, account.getId());
        return new BoundDevice(device, secret);
    }

    private Optional<IssuedDeviceToken> tryIssue(UUID deviceId, String deviceSecret, DeviceTokenTarget target) {
        Instant now = clock.instant();
        String presentedHash = secureTokens.sha256Hex(deviceSecret == null ? "" : deviceSecret.strip());
        Optional<DeviceCredential> device = deviceRepository.findById(deviceId).filter(found -> found.isUsableAt(now));
        boolean matches = device.map(found -> found.hasHash(presentedHash))
                .orElseGet(() -> MessageDigest.isEqual(DUMMY_DIGEST, presentedHash.getBytes(StandardCharsets.US_ASCII)));
        return device.filter(found -> matches)
                .filter(found -> accountRepository.findById(found.getAccountId()).filter(Account::isActive).isPresent())
                .map(found -> {
                    found.markUsed(now, LAST_USED_RESOLUTION);
                    return new IssuedDeviceToken(tokenIssuer.issue(found.getId(), found.getAccountId(), target.audience(),
                            target.scope()),
                            DeviceTokenIssuer.TTL, target.scope());
                });
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[DIGEST_LENGTH];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /** Bağlama sonucu: gizli anahtar yalnız burada, bir kez. */
    public record BoundDevice(DeviceCredential device, String secret) {

        @Override
        public String toString() {
            return "BoundDevice[deviceId=" + device.getId() + ", secret=***]";
        }
    }

    public record IssuedDeviceToken(String accessToken, Duration expiresIn, String scope) {

        @Override
        public String toString() {
            return "IssuedDeviceToken[accessToken=***, expiresIn=" + expiresIn + ", scope=" + scope + "]";
        }
    }
}
