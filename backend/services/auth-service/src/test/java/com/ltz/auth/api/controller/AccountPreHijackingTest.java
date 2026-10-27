package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.ltz.auth.ConcurrentRequests;
import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;
import com.ltz.auth.RecordingMailPortConfiguration.RecordingMailPort;

/**
 * N1 (hesap ön-ele-geçirme): saldırgan kurbanın e-postasıyla kendi parolasını kaydeder; kurban doğrulama e-postasını
 * alıp onaylasa bile hesap saldırganın kimlik bilgisiyle ACTIVE olmamalıdır. Kanıt: kaydı yapan tarafın parolası.
 */
@Import(RecordingMailPortConfiguration.class)
class AccountPreHijackingTest extends IntegrationTestSupport {

    private static final String ATTACKER_PASSWORD = "attacker-chosen-password-123";
    private static final String VICTIM_PASSWORD = "victim-own-password-456789";

    @Autowired
    private RecordingMailPort mailPort;

    @Test
    void kurbaninKoduKaydiYapanParolaOlmadanHesabiAktiflestirmez() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername(), ATTACKER_PASSWORD).andExpect(status().isAccepted());
        String codeInVictimsMailbox = mailPort.awaitLatestCode(email, 1);

        confirmWithoutProof(email, codeInVictimsMailbox).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_FAILED"));
        confirmWithPassword(email, codeInVictimsMailbox, VICTIM_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_FAILED"));

        assertThat(accountStatus(email)).isEqualTo("PENDING_VERIFICATION");
        assertThat(jdbc.queryForObject("select email_verified_at is null from accounts where email_normalized = "
                + "lower(?)", Boolean.class, email)).isTrue();
        // Kayıt sahibi (parolayı bilen) kendi kodunu onaylayabilir; ele geçirme yalnız kayıt sahibine açıktır.
        confirmWithPassword(email, codeInVictimsMailbox, ATTACKER_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void ikinciKayitMevcutBekleyenHesabinParolasiniDegistirmez() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername(), ATTACKER_PASSWORD).andExpect(status().isAccepted());
        String hashBefore = passwordHash(email);

        register(email, uniqueUsername(), VICTIM_PASSWORD).andExpect(status().isAccepted());

        assertThat(passwordHash(email)).as("kimlik bilgisi üzerine yazılmaz").isEqualTo(hashBefore);
        assertThat(accountCount(email)).isEqualTo(1);
        String code = mailPort.awaitLatestCode(email, 1);
        confirmWithPassword(email, code, VICTIM_PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void yanlisKanitDenemeleriKodDenemeHakkiniTuketmez() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername(), VALID_PASSWORD).andExpect(status().isAccepted());
        String code = mailPort.awaitLatestCode(email, 1);

        for (int attempt = 0; attempt < 10; attempt++) {
            confirmWithPassword(email, code, VICTIM_PASSWORD).andExpect(status().isBadRequest());
        }

        assertThat(jdbc.queryForObject("select max(attempts) from email_verification_codes where account_id = ?",
                Integer.class, accountId(email))).as("kod denemesi sayılmaz: saldırgan kurbanın kodunu yakamaz").isZero();
        confirm(email, code).andExpect(status().isNoContent());
        assertThat(accountStatus(email)).isEqualTo("ACTIVE");
    }

    @Test
    void esZamanliOnaylarTekBasariUretir_kodTekKullanimlik() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername(), VALID_PASSWORD).andExpect(status().isAccepted());
        String code = mailPort.awaitLatestCode(email, 1);

        List<Integer> statuses = ConcurrentRequests.run(6,
                index -> () -> confirm(email, code).andReturn().getResponse().getStatus());

        assertThat(statuses.stream().filter(value -> value == 204).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(value -> value == 400).count()).isEqualTo(5);
        assertThat(securityEventCount(accountId(email), "EMAIL_VERIFIED")).isEqualTo(1);
    }

    @Test
    void yanitKayitliOlmayanVeBekleyenHesapIcinAyniKalir() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername(), ATTACKER_PASSWORD).andExpect(status().isAccepted());
        String code = mailPort.awaitLatestCode(email, 1);

        String pending = confirmWithoutProof(email, code).andExpect(status().isBadRequest()).andReturn().getResponse()
                .getContentAsString();
        String unknown = confirmWithoutProof(uniqueEmail(), code).andExpect(status().isBadRequest()).andReturn()
                .getResponse().getContentAsString();

        assertThat(pending).as("hesap varlığı sızdırılmaz").isEqualTo(unknown);
    }

    private String passwordHash(String email) {
        return jdbc.queryForObject("select password_hash from accounts where email_normalized = lower(?)",
                String.class, email);
    }
}
