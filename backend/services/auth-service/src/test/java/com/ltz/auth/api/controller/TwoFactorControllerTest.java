package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;
import com.ltz.auth.infrastructure.security.SecretCipher;
import com.ltz.auth.infrastructure.security.SecretCipher.EncryptedSecret;
import com.ltz.auth.infrastructure.security.TotpCodes;
import com.nimbusds.jwt.SignedJWT;

import jakarta.servlet.http.Cookie;

@Import(RecordingMailPortConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class TwoFactorControllerTest extends IntegrationTestSupport {

    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int MAX_CHALLENGE_ATTEMPTS = 5;

    @Autowired
    private SecretCipher secretCipher;

    @Autowired
    private TotpCodes totpCodes;

    @Test
    void kurulumKimlikIster_etkinlestirmeDogruKodlaKurtarmaKodlariDondurur() throws Exception {
        mockMvc.perform(post(AuthApiPaths.TOTP_SETUP).with(xsrf())).andExpect(status().isUnauthorized());
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        Cookie access = loginSession(email).access();

        String setup = authenticated(AuthApiPaths.TOTP_SETUP, access, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.otpauthUri").value(startsWith("otpauth://totp/LTZ")))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(setup, "$.secret")).matches("^[A-Z2-7]{32}$");
        UUID accountId = accountId(email);
        authenticated(AuthApiPaths.TOTP_ACTIVATE, access, codeJson(wrongCode(accountId)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TWO_FACTOR_CODE"));

        List<String> recoveryCodes = activate(accountId, access);

        assertThat(recoveryCodes).hasSize(RECOVERY_CODE_COUNT).allMatch(code -> code.matches("^[A-Z2-9]{5}-[A-Z2-9]{5}$"));
        assertThat(jdbc.queryForObject("select count(*) from recovery_codes where account_id = ? and code_hash in (?)",
                Integer.class, accountId, recoveryCodes.getFirst())).isZero();
        authenticated(AuthApiPaths.TOTP_SETUP, access, null).andExpect(status().isConflict());
    }

    @Test
    void totpEtkinseParolaTokenVermez_ikinciAdimlaOturumAcilir_replayReddedilir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        activate(accountId, loginSession(email).access());

        String challengeId = passwordStep(email);
        String nextCode = codeAfterLastUse(accountId);
        String session = twoFactorStep(challengeId, nextCode, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andReturn().getResponse().getCookie(ACCESS_COOKIE).getValue();
        assertThat(SignedJWT.parse(session).getJWTClaimsSet().getStringListClaim("amr")).containsExactly("pwd", "otp");

        twoFactorStep(passwordStep(email), nextCode, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_FAILED"));
    }

    @Test
    void kurtarmaKoduBirKezKullanilir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        String recoveryCode = activate(accountId, loginSession(email).access()).getFirst();

        twoFactorStep(passwordStep(email), null, recoveryCode.toLowerCase()).andExpect(status().isOk());

        twoFactorStep(passwordStep(email), null, recoveryCode).andExpect(status().isUnauthorized());
        assertThat(securityEventCount(accountId, "RECOVERY_CODE_USED")).isEqualTo(1);
    }

    @Test
    void challengeDenemeSiniriVeSuresiAsilamaz() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        activate(accountId, loginSession(email).access());
        String challengeId = passwordStep(email);
        for (int attempt = 0; attempt < MAX_CHALLENGE_ATTEMPTS; attempt++) {
            twoFactorStep(challengeId, wrongCode(accountId), null).andExpect(status().isUnauthorized());
        }

        int afterLimit = twoFactorStep(challengeId, codeAfterLastUse(accountId), null).andReturn().getResponse().getStatus();
        assertThat(afterLimit).isIn(401, 429);

        jdbc.update("delete from attempt_throttles");
        String expired = passwordStep(email);
        jdbc.update("update mfa_challenges set created_at = created_at - interval '10 minutes', "
                + "expires_at = expires_at - interval '10 minutes' where account_id = ? and consumed_at is null", accountId);
        twoFactorStep(expired, codeAfterLastUse(accountId), null).andExpect(status().isUnauthorized());
    }

    @Test
    void ikinciFaktorAtlanamaz() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        activate(accountId(email), loginSession(email).access());

        login(email, VALID_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MFA_REQUIRED"))
                .andExpect(jsonPath("$.accessTokenExpiresAt").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        twoFactorStep("unknown-" + uniqueSuffix(), "123456", null).andExpect(status().isUnauthorized());
        mockMvc.perform(post(AuthApiPaths.LOGIN_TWO_FACTOR).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"x\",\"code\":\"123456\",\"recoveryCode\":\"AAAAA-AAAAA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void kapatmaParolaVeKodIster_sonrasindaTekAdimliGirisDoner() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        Cookie access = loginSession(email).access();
        activate(accountId, access);

        authenticated(AuthApiPaths.TOTP_DISABLE, access, disableJson("wrong-password-value", codeAfterLastUse(accountId)))
                .andExpect(status().isUnauthorized());
        authenticated(AuthApiPaths.TOTP_DISABLE, access, disableJson(VALID_PASSWORD, codeAfterLastUse(accountId)))
                .andExpect(status().isNoContent());

        login(email, VALID_PASSWORD).andExpect(jsonPath("$.status").value("AUTHENTICATED"));
        assertThat(securityEventCount(accountId, "TWO_FACTOR_DISABLED")).isEqualTo(1);
    }

    @Test
    void zorunluRolde2faYoksaOturumVerilmez_varsaKapatilamaz() throws Exception {
        String withoutTotp = registerActiveAccount(uniqueEmail(), uniqueUsername());
        grantRole(accountId(withoutTotp), "ADMIN");
        login(withoutTotp, VALID_PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_ENROLLMENT_REQUIRED"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        String moderator = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID moderatorId = accountId(moderator);
        Cookie access = loginSession(moderator).access();
        activate(moderatorId, access);
        grantRole(moderatorId, "MODERATOR");
        authenticated(AuthApiPaths.TOTP_DISABLE, access, disableJson(VALID_PASSWORD, codeAfterLastUse(moderatorId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_REQUIRED_FOR_ROLE"));
        login(moderator, VALID_PASSWORD).andExpect(jsonPath("$.status").value("MFA_REQUIRED"));
    }

    @Test
    void secretVeKodlarLoglaraVeVeritabaninaAcikYazilmaz(CapturedOutput output) throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        Cookie access = loginSession(email).access();
        String secret = JsonPath.read(authenticated(AuthApiPaths.TOTP_SETUP, access, null)
                .andReturn().getResponse().getContentAsString(), "$.secret");
        List<String> recoveryCodes = activateWithExistingSetup(accountId, access);

        assertThat(output.getAll()).doesNotContain(secret).doesNotContain(recoveryCodes.getFirst());
        Map<String, Object> stored = jdbc.queryForMap("select * from totp_credentials where account_id = ?", accountId);
        assertThat(new String((byte[]) stored.get("secret_ciphertext"), StandardCharsets.ISO_8859_1))
                .doesNotContain(secret);
    }

    private List<String> activate(UUID accountId, Cookie access) throws Exception {
        authenticated(AuthApiPaths.TOTP_SETUP, access, null).andExpect(status().isOk());
        return activateWithExistingSetup(accountId, access);
    }

    private List<String> activateWithExistingSetup(UUID accountId, Cookie access) throws Exception {
        String body = authenticated(AuthApiPaths.TOTP_ACTIVATE, access, codeJson(currentCode(accountId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.recoveryCodes");
    }

    private String passwordStep(String email) throws Exception {
        String body = login(email, VALID_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MFA_REQUIRED"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.challengeId");
    }

    private ResultActions twoFactorStep(String challengeId, String code, String recoveryCode) throws Exception {
        String factor = code != null ? "\"code\":\"" + code + "\"" : "\"recoveryCode\":\"" + recoveryCode + "\"";
        return mockMvc.perform(post(AuthApiPaths.LOGIN_TWO_FACTOR).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"" + challengeId + "\"," + factor + "}"));
    }

    private ResultActions authenticated(String path, Cookie access, String json) throws Exception {
        var request = post(path).with(xsrf()).cookie(access);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private static String codeJson(String code) {
        return "{\"code\":\"" + code + "\"}";
    }

    private static String disableJson(String password, String code) {
        return "{\"password\":\"" + password + "\",\"code\":\"" + code + "\"}";
    }

    private String currentCode(UUID accountId) {
        return totpCodes.codeAt(secret(accountId), totpCodes.stepAt(Instant.now()));
    }

    /** Son kabul edilen adımdan sonraki adımın kodu (saat penceresi içinde ve replay değil). */
    private String codeAfterLastUse(UUID accountId) {
        Long lastUsed = jdbc.queryForObject("select last_used_step from totp_credentials where account_id = ?",
                Long.class, accountId);
        return totpCodes.codeAt(secret(accountId), lastUsed + 1);
    }

    private String wrongCode(UUID accountId) {
        byte[] secret = secret(accountId);
        long step = totpCodes.stepAt(Instant.now());
        List<String> valid = List.of(totpCodes.codeAt(secret, step - 1), totpCodes.codeAt(secret, step),
                totpCodes.codeAt(secret, step + 1), totpCodes.codeAt(secret, step + 2));
        return valid.contains("000000") ? "999999" : "000000";
    }

    private byte[] secret(UUID accountId) {
        Map<String, Object> row = jdbc.queryForMap(
                "select secret_ciphertext, secret_nonce, key_version from totp_credentials where account_id = ?", accountId);
        return secretCipher.decrypt(new EncryptedSecret((byte[]) row.get("secret_ciphertext"),
                (byte[]) row.get("secret_nonce"), ((Number) row.get("key_version")).shortValue()), accountId);
    }
}
