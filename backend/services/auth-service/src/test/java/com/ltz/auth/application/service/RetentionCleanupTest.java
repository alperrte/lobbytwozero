package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.ConcurrentRequests;
import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;
import com.ltz.auth.infrastructure.config.CleanupProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.AuthDataEraser;
import com.ltz.auth.infrastructure.persistence.repository.RetentionCleanupRepository;

@Import(RecordingMailPortConfiguration.class)
class RetentionCleanupTest extends IntegrationTestSupport {

    private static final int BATCH = 1000;

    @Autowired
    private RetentionCleanupService cleanup;
    @Autowired
    private RetentionCleanupRepository cleanupRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private AuthDataEraser authDataEraser;
    @Autowired
    private SecurityEventRecorder securityEventRecorder;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private Clock clock;

    @Test
    void suresiDolanGeciciKayitlarSilinir_tazeOlanlarKorunur() {
        String oldFlow = hash();
        String freshFlow = hash();
        String oldNonce = hash();
        String freshNonce = hash();
        String oldProof = hash();
        String staleThrottle = hash();
        String activeLock = hash();
        jdbc.update("insert into oauth_flows (state_hash, provider, created_at, expires_at) values "
                + "(?, 'STEAM', now() - interval '4 hours', now() - interval '3 hours'), "
                + "(?, 'STEAM', now(), now() + interval '10 minutes')", oldFlow, freshFlow);
        jdbc.update("insert into openid_nonces (nonce_hash, seen_at) values "
                + "(?, now() - interval '3 hours'), (?, now() - interval '10 minutes')", oldNonce, freshNonce);
        jdbc.update("insert into steam_registrations (token_hash, steam_id, created_at, expires_at) values "
                + "(?, '76561190000000001', now() - interval '4 hours', now() - interval '3 hours')", oldProof);
        jdbc.update("insert into attempt_throttles (key_hash, failed_attempts, window_started_at, locked_until, "
                + "updated_at) values (?, 5, now() - interval '3 hours', now() - interval '2 hours', "
                + "now() - interval '2 hours'), (?, 5, now(), now() + interval '10 minutes', now())",
                staleThrottle, activeLock);

        cleanup.run();

        assertThat(exists("oauth_flows", "state_hash", oldFlow)).isFalse();
        assertThat(exists("oauth_flows", "state_hash", freshFlow)).isTrue();
        assertThat(exists("openid_nonces", "nonce_hash", oldNonce)).isFalse();
        assertThat(exists("openid_nonces", "nonce_hash", freshNonce)).as("yakın nonce replay'i engellemeye devam eder")
                .isTrue();
        assertThat(exists("steam_registrations", "token_hash", oldProof)).isFalse();
        assertThat(exists("attempt_throttles", "key_hash", staleThrottle)).isFalse();
        assertThat(exists("attempt_throttles", "key_hash", activeLock)).as("aktif kilit asla silinmez").isTrue();
    }

    @Test
    void suresiDolanKodTokenOturumVeChallengeSilinir() throws Exception {
        UUID accountId = accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
        UUID codeId = UUID.randomUUID();
        UUID freshCodeId = jdbc.queryForObject("select id from email_verification_codes where account_id = ? "
                + "and consumed_at is null", UUID.class, accountId);
        UUID resetId = UUID.randomUUID();
        jdbc.update("insert into email_verification_codes (id, account_id, purpose, code_hash, attempts, created_at, "
                + "expires_at, consumed_at) values (?, ?, 'EMAIL_VERIFICATION', ?, 0, now() - interval '4 days', "
                + "now() - interval '3 days', now() - interval '3 days')", codeId, accountId, hash());
        jdbc.update("insert into password_reset_tokens (id, account_id, token_hash, created_at, expires_at) values "
                + "(?, ?, ?, now() - interval '4 days', now() - interval '3 days')", resetId, accountId, hash());

        cleanup.run();

        assertThat(exists("email_verification_codes", "id", codeId)).isFalse();
        assertThat(exists("email_verification_codes", "id", freshCodeId)).isTrue();
        assertThat(exists("password_reset_tokens", "id", resetId)).isFalse();
    }

    @Test
    void dogrulanmamisHesapSuresiDolunca_anonimlesirEpostaSerbestKalirOlaylarKorunur() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername(), VALID_PASSWORD).andExpect(status().isAccepted());
        UUID accountId = accountId(email);
        String freshEmail = uniqueEmail();
        register(freshEmail, uniqueUsername(), VALID_PASSWORD).andExpect(status().isAccepted());
        String activeEmail = registerActiveAccount(uniqueEmail(), uniqueUsername());
        jdbc.update("update accounts set created_at = now() - interval '3 days' where id = ? or email_normalized = lower(?)",
                accountId, activeEmail);

        cleanup.run();

        assertThat(jdbc.queryForObject("select status from accounts where id = ?", String.class, accountId))
                .isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("select count(*) from email_verification_codes where account_id = ?",
                Integer.class, accountId)).isZero();
        assertThat(securityEventCount(accountId, "PENDING_ACCOUNT_EXPIRED")).isEqualTo(1);
        assertThat(securityEventCount(accountId, "ACCOUNT_REGISTERED")).as("denetim kayıtları silinmez").isEqualTo(1);
        assertThat(accountStatus(freshEmail)).as("süresi dolmamış hesap korunur").isEqualTo("PENDING_VERIFICATION");
        assertThat(accountStatus(activeEmail)).as("aktif hesaba dokunulmaz").isEqualTo("ACTIVE");
        register(email, uniqueUsername(), VALID_PASSWORD).andExpect(status().isAccepted());
        assertThat(accountStatus(email)).as("e-posta yeniden kullanılabilir").isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void temizlikIdempotent_ikinciCalismaYeniOlayUretmez() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername(), VALID_PASSWORD).andExpect(status().isAccepted());
        UUID accountId = accountId(email);
        jdbc.update("update accounts set created_at = now() - interval '3 days' where id = ?", accountId);

        cleanup.run();
        cleanup.run();

        assertThat(securityEventCount(accountId, "PENDING_ACCOUNT_EXPIRED")).isEqualTo(1);
    }

    @Test
    void eszamanliTemizlikler_hesabiTekKezIsler() throws Exception {
        List<UUID> accounts = new java.util.ArrayList<>();
        for (int index = 0; index < 4; index++) {
            String email = uniqueEmail();
            register(email, uniqueUsername(), VALID_PASSWORD).andExpect(status().isAccepted());
            accounts.add(accountId(email));
        }
        jdbc.update("update accounts set created_at = now() - interval '3 days' where id in (?, ?, ?, ?)",
                accounts.toArray());

        ConcurrentRequests.run(4, index -> (Callable<Integer>) () -> cleanup.run().total());

        for (UUID accountId : accounts) {
            assertThat(jdbc.queryForObject("select status from accounts where id = ?", String.class, accountId))
                    .isEqualTo("DELETED");
            assertThat(securityEventCount(accountId, "PENDING_ACCOUNT_EXPIRED")).isEqualTo(1);
        }
    }

    @Test
    void kucukPartiBoyutuylaDaTumSuresiDolanKayitlarBosaltilir() {
        List<String> hashes = List.of(hash(), hash(), hash(), hash(), hash());
        hashes.forEach(value -> jdbc.update("insert into openid_nonces (nonce_hash, seen_at) values "
                + "(?, now() - interval '5 hours')", value));

        RetentionCleanupService small = serviceWith(properties(2, null));
        small.run();

        hashes.forEach(value -> assertThat(exists("openid_nonces", "nonce_hash", value)).isFalse());
    }

    @Test
    void guvenlikOlayiSaklamaSuresiYapilandirilmadikcaOlaySilinmez() throws Exception {
        UUID accountId = accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
        UUID oldEvent = UUID.randomUUID();
        jdbc.update("insert into security_events (id, account_id, type, created_at) values "
                + "(?, ?, 'LOGIN_SUCCEEDED', now() - interval '5 years')", oldEvent, accountId);

        cleanup.run();
        assertThat(exists("security_events", "id", oldEvent)).as("karar bekliyor: varsayılan silme yok").isTrue();

        serviceWith(properties(BATCH, Duration.ofDays(365))).run();
        assertThat(exists("security_events", "id", oldEvent)).isFalse();
        assertThat(securityEventCount(accountId, "ACCOUNT_REGISTERED")).as("yeni olaylar korunur").isEqualTo(1);
    }

    private RetentionCleanupService serviceWith(CleanupProperties properties) {
        return new RetentionCleanupService(cleanupRepository, accountRepository, authDataEraser,
                securityEventRecorder, properties, transactionTemplate, clock);
    }

    private static CleanupProperties properties(int batchSize, Duration securityEventRetention) {
        return new CleanupProperties(true, Duration.ofHours(1), batchSize, Duration.ofHours(24), Duration.ofHours(1),
                Duration.ofHours(1), Duration.ofHours(1), Duration.ofDays(2), Duration.ofDays(7),
                securityEventRetention);
    }

    private boolean exists(String table, String column, Object value) {
        // Tablo/kolon adları yalnız bu sınıftaki sabit çağrılardan gelir.
        return jdbc.queryForObject("select count(*) from " + table + " where " + column + " = ?", Integer.class,
                value) > 0;
    }

    private static String hash() {
        String half = UUID.randomUUID().toString().replace("-", "");
        return half + half;
    }
}
