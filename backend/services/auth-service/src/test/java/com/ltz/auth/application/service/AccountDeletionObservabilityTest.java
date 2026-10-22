package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ltz.auth.AccountDeletionTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;
import com.ltz.auth.api.controller.AuthApiPaths;
import com.ltz.auth.infrastructure.config.DeletionProperties;

/**
 * N3/N4: ulaşılamayan servis sessizce başarı sayılmaz; takılı görevler yönetici uç noktasında görünür, denetim tablosu
 * her denemede şişmez; onaylanan silmede bekleyen kimlik doğrulama artıkları geçersiz kılınır.
 */
@Import(RecordingMailPortConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AccountDeletionObservabilityTest extends AccountDeletionTestSupport {

    private static final int EXTRA_ROUNDS = 2;

    @Autowired
    private DeletionProperties properties;

    @Test
    void takiliGorevlerYoneticiyeGorunur_olayHerDenemedeYazilmaz_takilinca1KezUyarilir(CapturedOutput output)
            throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = deleteAccountConfirmed(email);

        for (int round = 0; round < properties.stuckAttempts() + EXTRA_ROUNDS; round++) {
            makeDue(accountId);
            assertThat(coordinator.processDue()).isZero();
        }

        // 2 görev (user, mail): her biri için yalnız 1. deneme ve takılma eşiğinde olay.
        assertThat(securityEventCount(accountId, "ACCOUNT_DELETION_TASK_FAILED")).isEqualTo(4);
        assertThat(output.getAll()).contains("Account deletion task stuck");
        assertThat(output.getAll()).doesNotContainIgnoringCase(email);
        String adminBearer = bearerFor(newAdmin());

        String body = mockMvc.perform(get(AuthApiPaths.ADMIN_DELETIONS_PENDING + "?limit=200")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.accountId=='" + accountId + "' && @.stuck==true)]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.accountId=='" + accountId
                        + "')].lastError").value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.is("SERVICE_ERASURE_UNAVAILABLE"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContainIgnoringCase(email).doesNotContain(usernameOf(accountId));
        assertThat(jdbc.queryForObject("select status from accounts where id = ?", String.class, accountId))
                .as("eksik servis başarı sayılmaz").isEqualTo("DELETION_REQUESTED");
    }

    @Test
    void yoneticiOlmayanBekleyenSilmeleriGoremez() throws Exception {
        mockMvc.perform(get(AuthApiPaths.ADMIN_DELETIONS_PENDING)).andExpect(status().isUnauthorized());

        UUID player = accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
        mockMvc.perform(get(AuthApiPaths.ADMIN_DELETIONS_PENDING).header(HttpHeaders.AUTHORIZATION, bearerFor(player)))
                .andExpect(status().isForbidden());
    }

    @Test
    void limitAralikDisiDegerlerdeSinirlanir_hataVermez() throws Exception {
        String adminBearer = bearerFor(newAdmin());

        mockMvc.perform(get(AuthApiPaths.ADMIN_DELETIONS_PENDING + "?limit=-5")
                .header(HttpHeaders.AUTHORIZATION, adminBearer)).andExpect(status().isOk());
        mockMvc.perform(get(AuthApiPaths.ADMIN_DELETIONS_PENDING + "?limit=100000")
                .header(HttpHeaders.AUTHORIZATION, adminBearer)).andExpect(status().isOk());
    }

    @Test
    void silmeOnayindaBekleyenSifirlamaKoduMfaVeSteamKanitlariGecersizKilinir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        String steamId = "76561190" + String.format("%09d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1_000_000_000L);
        String proofHash = UUID.randomUUID().toString().replace("-", "").repeat(2);
        jdbc.update("insert into external_identities (id, account_id, provider, provider_subject, linked_at) "
                + "values (?, ?, 'STEAM', ?, now())", UUID.randomUUID(), accountId, steamId);
        jdbc.update("insert into steam_registrations (token_hash, steam_id, created_at, expires_at, purpose) values "
                + "(?, ?, now(), now() + interval '30 minutes', 'EMAIL_VERIFICATION')", proofHash, steamId);
        jdbc.update("insert into password_reset_tokens (id, account_id, token_hash, created_at, expires_at) values "
                + "(?, ?, ?, now(), now() + interval '30 minutes')", UUID.randomUUID(), accountId,
                UUID.randomUUID().toString().replace("-", "").repeat(2));

        deleteAccountConfirmed(email);

        assertThat(jdbc.queryForObject("select count(*) from steam_registrations where token_hash = ?",
                Integer.class, proofHash)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from password_reset_tokens where account_id = ?",
                Integer.class, accountId)).isZero();
    }

    private void makeDue(UUID accountId) {
        jdbc.update("update account_deletion_tasks set next_attempt_at = now() - interval '1 second' "
                + "where account_id = ? and status = 'PENDING'", accountId);
    }

    private UUID newAdmin() throws Exception {
        UUID admin = accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
        enableTotp(admin);
        grantRole(admin, "ADMIN");
        return admin;
    }
}
