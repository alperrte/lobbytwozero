package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.infrastructure.config.TwoFactorProperties;
import com.ltz.auth.infrastructure.config.VerificationProperties;
import com.ltz.auth.infrastructure.security.SecretCipher;
import com.ltz.auth.infrastructure.security.SecureTokens;
import com.ltz.auth.infrastructure.security.TotpCodes;
import com.ltz.auth.infrastructure.security.VerificationCodeHasher;

/**
 * Anahtar rotasyonu (S9): güncel TOTP anahtarı sürüm 2'dir, sürüm 1 yalnız çözmede kullanılan önceki anahtardır; HMAC için
 * önceki anahtar ayrıca verilmiştir. Eski anahtarla yazılmış kayıtlar çalışmaya devam eder ve TOTP başarısında yenilenir.
 */
class KeyRotationTest extends IntegrationTestSupport {

    private static final String OLD_TOTP_KEY = randomKey();
    private static final String OLD_HMAC_KEY = randomKey();
    private static final String RECOVERY_CODE_DOMAIN = "RECOVERY_CODE";

    @DynamicPropertySource
    static void rotatedKeys(DynamicPropertyRegistry registry) {
        registry.add("ltz.auth.two-factor.encryption-key-version", () -> "2");
        registry.add("ltz.auth.two-factor.previous-encryption-keys", () -> "1=" + OLD_TOTP_KEY);
        registry.add("ltz.auth.verification.previous-hmac-key", () -> OLD_HMAC_KEY);
    }

    @Autowired
    private TotpCodes totpCodes;

    @Autowired
    private SecureTokens secureTokens;

    /** Uygulamanın güncel (sürüm 2) şifreleyicisi; ana sınıftaki alan özel olduğundan ayrıca bağlanır. */
    @Autowired
    private SecretCipher appCipher;

    @Test
    void eskiSurumdekiTotpSirriCalisirVeBasariliDogrulamadaYeniAnahtarlaYenilenir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID account = accountId(email);
        byte[] secret = totpCodes.newSecret();
        insertTotp(account, secret, 1);
        byte[] before = ciphertextOf(account);

        String challenge = passwordStep(email);
        twoFactorStep(challenge, totpCodes.codeAt(secret, totpCodes.stepAt(Instant.now()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"));

        assertThat(keyVersionOf(account)).as("başarılı doğrulamada güncel sürüme geçer").isEqualTo(2);
        assertThat(ciphertextOf(account)).isNotEqualTo(before);
        // Yenilenen satır güncel anahtarla da aynı gizli anahtarı verir (testin yardımcısı güncel şifreleyiciyi kullanır).
        assertThat(currentTotpCode(account)).isEqualTo(totpCodes.codeAt(secret, totpCodes.stepAt(Instant.now())));
    }

    @Test
    void yanlisKodSatiriYenilemez() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID account = accountId(email);
        byte[] secret = totpCodes.newSecret();
        insertTotp(account, secret, 1);
        String right = totpCodes.codeAt(secret, totpCodes.stepAt(Instant.now()));
        String wrong = right.equals("000000") ? "000001" : "000000";

        twoFactorStep(passwordStep(email), wrong).andExpect(status().isUnauthorized());

        assertThat(keyVersionOf(account)).isEqualTo(1);
    }

    @Test
    void yeniTotpKurulumuGuncelSurumleSifrelenir() throws Exception {
        UUID account = accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));

        mockMvc.perform(post(AuthApiPaths.TOTP_SETUP).header("Authorization", bearerFor(account)))
                .andExpect(status().isOk());

        assertThat(keyVersionOf(account)).isEqualTo(2);
    }

    @Test
    void oncekiHmacAnahtariylaKayitliKurtarmaKoduCalisirVeTukenir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID account = accountId(email);
        insertTotp(account, totpCodes.newSecret(), 2);
        String recoveryCode = "ABCDE-FGHJK";
        // Kod, anahtar döndürülmeden önce önceki HMAC anahtarıyla özetlenmiş gibi kaydedilir.
        String legacyHash = hasherWith(OLD_HMAC_KEY)
                .hash(RECOVERY_CODE_DOMAIN, account + ":" + secureTokens.normalizeRecoveryCode(recoveryCode));
        jdbc.update("insert into recovery_codes (id, account_id, code_hash, created_at) "
                + "values (gen_random_uuid(), ?, ?, now())", account, legacyHash);

        twoFactorStepWithRecovery(passwordStep(email), recoveryCode).andExpect(status().isOk());
        // Tek kullanımlık: eski özetle kayıtlı kod da ikinci kez geçmez.
        twoFactorStepWithRecovery(passwordStep(email), recoveryCode).andExpect(status().isUnauthorized());
        assertThat(securityEventCount(account, "RECOVERY_CODE_USED")).isEqualTo(1);
    }

    // ---- Yardımcılar ----

    private void insertTotp(UUID account, byte[] secret, int version) {
        // Satır, belirtilen sürümün anahtarıyla şifrelenir: sürüm 1 = önceki anahtar, sürüm 2 = uygulamanın güncel anahtarı.
        SecretCipher.EncryptedSecret encrypted = version == 1
                ? cipherWith(OLD_TOTP_KEY, 1).encrypt(secret, account)
                : appCipher.encrypt(secret, account);
        jdbc.update("insert into totp_credentials (account_id, secret_ciphertext, secret_nonce, key_version, status, "
                + "created_at, activated_at) values (?, ?, ?, ?, 'ACTIVE', now(), now())", account,
                encrypted.ciphertext(), encrypted.nonce(), (short) version);
    }

    private static SecretCipher cipherWith(String key, int version) {
        return new SecretCipher(new TwoFactorProperties("LTZ", key, Duration.ofMinutes(5), 5, 10, 1, Set.of(),
                version, null));
    }

    private static VerificationCodeHasher hasherWith(String key) {
        return new VerificationCodeHasher(new VerificationProperties(Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 5,
                key, null));
    }

    private String passwordStep(String email) throws Exception {
        String body = login(email, VALID_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MFA_REQUIRED")).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.challengeId");
    }

    private ResultActions twoFactorStep(String challenge, String code) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.LOGIN_TWO_FACTOR).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"%s\",\"code\":\"%s\"}".formatted(challenge, code)));
    }

    private ResultActions twoFactorStepWithRecovery(String challenge, String recoveryCode) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.LOGIN_TWO_FACTOR).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"%s\",\"recoveryCode\":\"%s\"}".formatted(challenge, recoveryCode)));
    }

    private int keyVersionOf(UUID account) {
        return jdbc.queryForObject("select key_version from totp_credentials where account_id = ?", Integer.class, account);
    }

    private byte[] ciphertextOf(UUID account) {
        return jdbc.queryForObject("select secret_ciphertext from totp_credentials where account_id = ?", byte[].class,
                account);
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
