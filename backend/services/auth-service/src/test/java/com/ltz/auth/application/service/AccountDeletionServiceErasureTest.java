package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtException;

import com.ltz.auth.AccountDeletionTestSupport;
import com.ltz.auth.ErasureTargetStub.Received;
import com.ltz.auth.RecordingMailPortConfiguration;
import com.ltz.auth.ServiceTokenContract;
import com.ltz.auth.api.controller.AuthApiPaths;
import com.nimbusds.jwt.SignedJWT;

/**
 * Uçtan uca D4 silme (gerçek uygulama bağlamı + gerçek HTTP + servis token'ı). Hedef taklidi, gelen token'ı hedef
 * servislerin uygulaması zorunlu kurallarla doğrular ({@link ServiceTokenContract}); geçerse 204, geçmezse 401 döner.
 */
@Import(RecordingMailPortConfiguration.class)
class AccountDeletionServiceErasureTest extends AccountDeletionTestSupport {

    private static final Clock CLOCK = Clock.systemUTC();
    private static final String ISSUER = "ltz-auth";
    private static final String PATH_PREFIX = "/internal/v1/accounts/";
    private static final Set<String> TARGET_AUDIENCES = Set.of("user-service", "mail-service");
    private static final int CONFIRMED = 204;
    private static final int REJECTED = 401;

    @Test
    void hedeflerSozlesmeyeUygunTokeniOnaylayincaHesapSilinir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = deleteAccountConfirmed(email);
        ERASURE_TARGETS.respondWith(ServiceTokenEnforcement::status);

        assertThat(coordinator.processDue()).isEqualTo(2);

        List<Received> calls = callsFor(accountId);
        assertThat(calls).extracting(ServiceTokenEnforcement::audience)
                .containsExactlyInAnyOrder("user-service", "mail-service");
        assertThat(calls).allSatisfy(call -> assertThat(call.method()).isEqualTo("DELETE"));
        assertThat(jdbc.queryForObject("select status from accounts where id = ?", String.class, accountId))
                .isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("select email from accounts where id = ?", String.class, accountId))
                .doesNotContainIgnoringCase(email);
        assertThat(jdbc.queryForObject("select status from account_deletion_requests where account_id = ?",
                String.class, accountId)).isEqualTo("COMPLETED");
        assertThat(securityEventCount(accountId, "ACCOUNT_DELETION_COMPLETED")).isEqualTo(1);
    }

    @Test
    void birHedef202DonerseSilmeTamamlanmazYalnizOHedefYenidenDenenir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = deleteAccountConfirmed(email);
        ERASURE_TARGETS.respondWith(request -> "mail-service".equals(ServiceTokenEnforcement.audience(request))
                ? 202 : ServiceTokenEnforcement.status(request));

        assertThat(coordinator.processDue()).isEqualTo(1);

        assertThat(jdbc.queryForObject("select last_error from account_deletion_tasks where account_id = ? "
                + "and service = 'mail'", String.class, accountId)).isEqualTo("SERVICE_ERASURE_HTTP_202");
        assertThat(jdbc.queryForObject("select status from account_deletion_tasks where account_id = ? "
                + "and service = 'user'", String.class, accountId)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select status from accounts where id = ?", String.class, accountId))
                .isEqualTo("DELETION_REQUESTED");
        assertThat(jdbc.queryForObject("select email from accounts where id = ?", String.class, accountId))
                .isEqualToIgnoringCase(email);
    }

    @Test
    void servisTokeniKullaniciTokeniYerineGecemez() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = deleteAccountConfirmed(email);
        ERASURE_TARGETS.respondWith(503);
        coordinator.processDue();
        String serviceToken = ServiceTokenContract.bearerToken(callsFor(accountId).getFirst().authorization());

        mockMvc.perform(post(AuthApiPaths.TOTP_SETUP).header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken))
                .andExpect(status().isUnauthorized());
    }

    private static List<Received> callsFor(UUID accountId) {
        return ERASURE_TARGETS.received().stream()
                .filter(request -> request.path().equals(PATH_PREFIX + accountId))
                .toList();
    }

    /** Hedef servisin yapması gereken: token'ı sözleşmeyle doğrula, yol kimliğiyle eşleştir; aksi halde 401. */
    private static final class ServiceTokenEnforcement {

        private ServiceTokenEnforcement() {
        }

        static int status(Received request) {
            String audience = audience(request);
            if (!TARGET_AUDIENCES.contains(audience) || !request.path().startsWith(PATH_PREFIX)) {
                return REJECTED;
            }
            try {
                UUID pathAccountId = UUID.fromString(request.path().substring(PATH_PREFIX.length()));
                ServiceTokenContract.verify(ServiceTokenContract.bearerToken(request.authorization()),
                        (RSAPublicKey) TEST_JWT_KEYS.getPublic(), ISSUER, audience, pathAccountId, CLOCK);
                return CONFIRMED;
            } catch (JwtException | IllegalArgumentException _) {
                return REJECTED;
            }
        }

        static String audience(Received request) {
            try {
                List<String> audience = SignedJWT.parse(ServiceTokenContract.bearerToken(request.authorization()))
                        .getJWTClaimsSet().getAudience();
                return audience.size() == 1 ? audience.getFirst() : "";
            } catch (ParseException | IllegalArgumentException _) {
                return "";
            }
        }
    }
}
