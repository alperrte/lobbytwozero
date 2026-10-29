package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;

import com.ltz.auth.ConcurrentRequests;
import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;
import com.ltz.auth.RecordingMailPortConfiguration.RecordingMailPort;
import com.ltz.auth.domain.enums.VerificationPurpose;
import com.ltz.auth.infrastructure.security.VerificationCodeHasher;

@Import(RecordingMailPortConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class EmailVerificationControllerTest extends IntegrationTestSupport {

    private static final int MAX_ATTEMPTS = 5;
    private static final int DAILY_LIMIT = 5;
    private static final String PAST_COOLDOWN = "2 minutes";

    @Autowired
    private RecordingMailPort mailPort;

    @Autowired
    private VerificationCodeHasher codeHasher;

    @Test
    void dogruKodHesabiAktiflestirirKodIkinciKezKullanilamaz() throws Exception {
        String email = registerNewAccount();
        String code = mailPort.awaitLatestCode(email, 1);

        confirm(email, code).andExpect(status().isNoContent());

        UUID accountId = accountId(email);
        assertThat(accountStatus(email)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select email_verified_at is not null from accounts where id = ?",
                Boolean.class, accountId)).isTrue();
        assertThat(securityEventCount(accountId, "EMAIL_VERIFIED")).isEqualTo(1);
        confirm(email, code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VERIFICATION_FAILED"));
    }

    @Test
    void basindaSifirOlanKodDogrulanir() throws Exception {
        String email = registerNewAccount();
        mailPort.awaitLatestCode(email, 1);
        UUID accountId = accountId(email);
        String leadingZeroCode = "000123";
        jdbc.update("update email_verification_codes set code_hash = ? where account_id = ?",
                codeHasher.hash(accountId, VerificationPurpose.EMAIL_VERIFICATION, leadingZeroCode), accountId);

        confirm(email, leadingZeroCode).andExpect(status().isNoContent());
    }

    @Test
    void yanlisKodDenemeHakkiniTuketirHakBitinceDogruKodDaReddedilir() throws Exception {
        String email = registerNewAccount();
        String code = mailPort.awaitLatestCode(email, 1);
        String wrongCode = code.equals("111111") ? "222222" : "111111";

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            confirm(email, wrongCode).andExpect(status().isBadRequest());
        }

        confirm(email, code).andExpect(status().isBadRequest());
        assertThat(accountStatus(email)).isEqualTo("PENDING_VERIFICATION");
        assertThat(codeAttempts(accountId(email))).isEqualTo(MAX_ATTEMPTS);
    }

    @Test
    void suresiDolmusKodReddedilir() throws Exception {
        String email = registerNewAccount();
        String code = mailPort.awaitLatestCode(email, 1);
        shiftCodesBack(accountId(email), "11 minutes");

        confirm(email, code).andExpect(status().isBadRequest());
        assertThat(accountStatus(email)).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void bilinmeyenEPostaVeYanlisKodAyniHatayiAlir() throws Exception {
        String email = registerNewAccount();
        String code = mailPort.awaitLatestCode(email, 1);
        String wrongCode = code.equals("111111") ? "222222" : "111111";

        String wrongCodeBody = confirm(email, wrongCode).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        String unknownEmailBody = confirm(uniqueEmail(), code).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmailBody).isEqualTo(wrongCodeBody);
    }

    @Test
    void yenidenGondermeBeklemeSuresindeYeniKodUretmez() throws Exception {
        String email = registerNewAccount();
        mailPort.awaitLatestCode(email, 1);

        resend(email).andExpect(status().isAccepted());

        assertThat(codeCount(accountId(email))).isEqualTo(1);
        assertThat(securityEventCount(accountId(email), "VERIFICATION_CODE_RESEND_THROTTLED")).isEqualTo(1);
    }

    @Test
    void yenidenGondermeEskiKoduGecersizKilarYeniKodCalisir() throws Exception {
        String email = registerNewAccount();
        String oldCode = mailPort.awaitLatestCode(email, 1);
        shiftCodesBack(accountId(email), PAST_COOLDOWN);

        resend(email).andExpect(status().isAccepted());
        String newCode = mailPort.awaitLatestCode(email, 2);

        if (!newCode.equals(oldCode)) {
            confirm(email, oldCode).andExpect(status().isBadRequest());
        }
        confirm(email, newCode).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("""
                select count(*) from email_verification_codes where account_id = ? and invalidation_reason = 'SUPERSEDED'
                """, Integer.class, accountId(email))).isEqualTo(1);
    }

    @Test
    void gunlukKodSiniriAsilamaz() throws Exception {
        String email = registerNewAccount();
        UUID accountId = accountId(email);
        for (int issued = 1; issued < DAILY_LIMIT; issued++) {
            shiftCodesBack(accountId, PAST_COOLDOWN);
            resend(email).andExpect(status().isAccepted());
        }
        assertThat(codeCount(accountId)).isEqualTo(DAILY_LIMIT);

        shiftCodesBack(accountId, PAST_COOLDOWN);
        resend(email).andExpect(status().isAccepted());

        assertThat(codeCount(accountId)).isEqualTo(DAILY_LIMIT);
        assertThat(securityEventCount(accountId, "VERIFICATION_CODE_RESEND_THROTTLED")).isEqualTo(1);
    }

    @Test
    void bilinmeyenVeDogrulanmisHesapIcinYenidenGondermeAyniYanitiVerir() throws Exception {
        String email = registerNewAccount();
        confirm(email, mailPort.awaitLatestCode(email, 1)).andExpect(status().isNoContent());
        shiftCodesBack(accountId(email), PAST_COOLDOWN);

        resend(email).andExpect(status().isAccepted())
                .andExpect(content().json("{\"nextStep\":\"VERIFY_EMAIL\"}", true));
        resend(uniqueEmail()).andExpect(status().isAccepted())
                .andExpect(content().json("{\"nextStep\":\"VERIFY_EMAIL\"}", true));

        assertThat(codeCount(accountId(email))).isEqualTo(1);
    }

    @Test
    void eszamanliIkiDogruDogrulamadanYalnizBiriBasarili() throws Exception {
        String email = registerNewAccount();
        String code = mailPort.awaitLatestCode(email, 1);

        List<Integer> statuses = ConcurrentRequests.run(2,
                index -> () -> confirm(email, code).andReturn().getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(204, 400);
        assertThat(securityEventCount(accountId(email), "EMAIL_VERIFIED")).isEqualTo(1);
    }

    @Test
    void eszamanliYanlisDenemelerDenemeSiniriniAsamaz() throws Exception {
        String email = registerNewAccount();
        String code = mailPort.awaitLatestCode(email, 1);
        String wrongCode = code.equals("111111") ? "222222" : "111111";

        ConcurrentRequests.run(MAX_ATTEMPTS * 2,
                index -> () -> confirm(email, wrongCode).andReturn().getResponse().getStatus());

        assertThat(codeAttempts(accountId(email))).isEqualTo(MAX_ATTEMPTS);
    }

    @Test
    void dogrulanmamisHesapOturumAlamaz() throws Exception {
        String email = registerNewAccount();
        mailPort.awaitLatestCode(email, 1);

        login(email, VALID_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(accountStatus(email)).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void parolaKodVeEPostaLoglaraYazilmaz(CapturedOutput output) throws Exception {
        String email = registerNewAccount();
        String code = mailPort.awaitLatestCode(email, 1);
        confirm(email, code.equals("111111") ? "222222" : "111111");
        confirm(email, code).andExpect(status().isNoContent());

        assertThat(output.getAll())
                .doesNotContain(VALID_PASSWORD)
                .doesNotContain(email)
                .doesNotContain("code=" + code)
                .doesNotContain("\"" + code + "\"");
    }

    private String registerNewAccount() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername(), VALID_PASSWORD).andExpect(status().isAccepted());
        return email;
    }

    private int codeAttempts(UUID accountId) {
        return jdbc.queryForObject("select max(attempts) from email_verification_codes where account_id = ?",
                Integer.class, accountId);
    }

    private int codeCount(UUID accountId) {
        return jdbc.queryForObject("select count(*) from email_verification_codes where account_id = ?",
                Integer.class, accountId);
    }
}
