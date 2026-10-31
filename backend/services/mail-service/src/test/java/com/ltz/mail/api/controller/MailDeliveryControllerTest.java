package com.ltz.mail.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.mail.IntegrationTestSupport;
import com.ltz.mail.domain.enums.SecurityNotificationType;
import com.nimbusds.jwt.JWTClaimsSet;

class MailDeliveryControllerTest extends IntegrationTestSupport {

    private static final String EMAIL = "kerem@example.com";

    private ResultActions postJson(String path, String authorization, String body) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request);
    }

    private ResultActions postCode(UUID account, UUID key, String authorization) throws Exception {
        return postJson(InternalApiPaths.VERIFICATION_CODES, authorization, verificationBody(key, account, EMAIL, "123456"));
    }

    @Test
    void verificationCodeIsQueuedEncryptedAndMasked() throws Exception {
        UUID account = newAccount();
        UUID key = UUID.randomUUID();

        postCode(account, key, bearer(account, MAIL_SEND_SCOPE)).andExpect(status().isAccepted());

        Map<String, Object> row = messageByKey(key);
        assertThat(row).containsEntry("status", "PENDING").containsEntry("type", "EMAIL_VERIFICATION")
                .containsEntry("recipient_email", EMAIL).containsEntry("recipient_masked", "k***@e***.com")
                .containsEntry("recipient_user_id", account).containsEntry("attempts", 0);
        assertThat(row.get("locale")).isNull();
        byte[] payload = (byte[]) row.get("payload_encrypted");
        assertThat(new String(payload, StandardCharsets.ISO_8859_1)).doesNotContain("123456");
        assertThat((String) row.get("request_hash")).hasSize(64).doesNotContain("123456");
    }

    @Test
    void localeIsStoredWhenGiven() throws Exception {
        UUID account = newAccount();
        UUID key = UUID.randomUUID();
        String body = verificationBody(key, account, EMAIL, "654321").replace("}", ",\"locale\":\"en\"}");

        postJson(InternalApiPaths.VERIFICATION_CODES, bearer(account, MAIL_SEND_SCOPE), body)
                .andExpect(status().isAccepted());

        assertThat(messageByKey(key)).containsEntry("locale", "en");
    }

    @Test
    void passwordResetAndDeletionLinksAreQueued() throws Exception {
        UUID account = newAccount();
        UUID resetKey = UUID.randomUUID();
        UUID deletionKey = UUID.randomUUID();

        postJson(InternalApiPaths.PASSWORD_RESETS, bearer(account, MAIL_SEND_SCOPE),
                resetBody(resetKey, account, EMAIL, RESET_LINK)).andExpect(status().isAccepted());
        String deletion = resetBody(deletionKey, account, EMAIL, RESET_LINK).replace("resetLink", "confirmationLink");
        postJson(InternalApiPaths.ACCOUNT_DELETIONS, bearer(account, MAIL_SEND_SCOPE), deletion)
                .andExpect(status().isAccepted());

        assertThat(messageByKey(resetKey)).containsEntry("type", "PASSWORD_RESET_LINK");
        assertThat(messageByKey(deletionKey)).containsEntry("type", "ACCOUNT_DELETION_CONFIRMATION");
    }

    @Test
    void everySecurityNotificationTypeIsQueued() throws Exception {
        UUID account = newAccount();
        for (SecurityNotificationType type : SecurityNotificationType.values()) {
            UUID key = UUID.randomUUID();
            postJson(InternalApiPaths.SECURITY_NOTIFICATIONS, bearer(account, MAIL_SEND_SCOPE),
                    notificationBody(key, account, EMAIL, type.name())).andExpect(status().isAccepted());
            assertThat(messageByKey(key)).containsEntry("type", type.mailType().name());
        }
        assertThat(messageCount()).isEqualTo(SecurityNotificationType.values().length);
    }

    @Test
    void futureOccurredAtIsRejected() throws Exception {
        UUID account = newAccount();
        String body = notificationBody(UUID.randomUUID(), account, EMAIL, "PASSWORD_CHANGED")
                .replaceAll("\"occurredAt\":\"[^\"]+\"", "\"occurredAt\":\"" + Instant.now().plusSeconds(3600) + "\"");

        postJson(InternalApiPaths.SECURITY_NOTIFICATIONS, bearer(account, MAIL_SEND_SCOPE), body)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_OCCURRED_AT"));
        assertThat(messageCount()).isZero();
    }

    @Test
    void sameKeyAndSameContentIsAcceptedOnceAndQueuedOnce() throws Exception {
        UUID account = newAccount();
        UUID key = UUID.randomUUID();

        postCode(account, key, bearer(account, MAIL_SEND_SCOPE)).andExpect(status().isAccepted());
        postCode(account, key, bearer(account, MAIL_SEND_SCOPE)).andExpect(status().isAccepted());

        assertThat(messageCount()).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentContentIsAConflict() throws Exception {
        UUID account = newAccount();
        UUID key = UUID.randomUUID();
        postCode(account, key, bearer(account, MAIL_SEND_SCOPE)).andExpect(status().isAccepted());

        postJson(InternalApiPaths.VERIFICATION_CODES, bearer(account, MAIL_SEND_SCOPE),
                verificationBody(key, account, EMAIL, "999999"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(messageCount()).isEqualTo(1);
    }

    @Test
    void concurrentRequestsWithTheSameKeyQueueOneMail() throws Exception {
        UUID account = newAccount();
        UUID key = UUID.randomUUID();
        String authorization = bearer(account, MAIL_SEND_SCOPE);
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                calls.add(() -> postCode(account, key, authorization).andReturn().getResponse().getStatus());
            }
            for (Future<Integer> result : pool.invokeAll(calls)) {
                assertThat(result.get()).isEqualTo(202);
            }
        }
        assertThat(messageCount()).isEqualTo(1);
    }

    @Test
    void linksOutsideThePolicyAreRejected() throws Exception {
        UUID account = newAccount();
        for (String link : List.of("https://evil.example/reset", "http://" + ALLOWED_LINK_HOST + "/reset",
                "https://user@" + ALLOWED_LINK_HOST + "/reset", "javascript:alert(1)", "not a url")) {
            postJson(InternalApiPaths.PASSWORD_RESETS, bearer(account, MAIL_SEND_SCOPE),
                    resetBody(UUID.randomUUID(), account, EMAIL, link))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_LINK"));
        }
        assertThat(messageCount()).isZero();
    }

    @Test
    void invalidBodiesAreRejectedWithoutEchoingValues() throws Exception {
        UUID account = newAccount();
        String authorization = bearer(account, MAIL_SEND_SCOPE);
        String badCode = verificationBody(UUID.randomUUID(), account, EMAIL, "12345");
        String badEmail = verificationBody(UUID.randomUUID(), account, "not-an-email", "123456");
        String badLocale = verificationBody(UUID.randomUUID(), account, EMAIL, "123456")
                .replace("}", ",\"locale\":\"de\"}");

        for (String body : List.of(badCode, badEmail, badLocale)) {
            postJson(InternalApiPaths.VERIFICATION_CODES, authorization, body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        String unknownField = verificationBody(UUID.randomUUID(), account, EMAIL, "123456")
                .replace("}", ",\"role\":\"ADMIN\"}");
        postJson(InternalApiPaths.VERIFICATION_CODES, authorization, unknownField).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        postJson(InternalApiPaths.VERIFICATION_CODES, authorization, "{not json").andExpect(status().isBadRequest());
        assertThat(messageCount()).isZero();
    }

    @Test
    void oversizedBodyIsRejected() throws Exception {
        UUID account = newAccount();

        postJson(InternalApiPaths.VERIFICATION_CODES, bearer(account, MAIL_SEND_SCOPE), "x".repeat(20_000))
                .andExpect(status().isContentTooLarge()).andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void requestsWithoutAValidServiceTokenAreRejected() throws Exception {
        UUID account = newAccount();
        UUID key = UUID.randomUUID();
        Date past = Date.from(Instant.now().minusSeconds(300));
        Date farFuture = Date.from(Instant.now().plusSeconds(600));
        UnaryOperator<JWTClaimsSet.Builder> expired = claims -> claims.issueTime(past).expirationTime(past);
        UnaryOperator<JWTClaimsSet.Builder> tooLong = claims -> claims.expirationTime(farFuture);
        List<String> rejected = List.of(
                serviceToken(account, MAIL_SEND_SCOPE, claims -> claims.audience("user-service")),
                serviceToken(account, MAIL_SEND_SCOPE, claims -> claims.subject("svc:social-service")),
                serviceToken(account, MAIL_SEND_SCOPE, claims -> claims.issuer("someone-else")),
                serviceToken(account, MAIL_SEND_SCOPE, claims -> claims.issueTime(null)),
                serviceToken(account, MAIL_SEND_SCOPE, claims -> claims.jwtID(null)),
                serviceToken(account, MAIL_SEND_SCOPE, expired),
                serviceToken(account, MAIL_SEND_SCOPE, tooLong),
                userAccessToken(account));

        postCode(account, key, null).andExpect(status().isUnauthorized());
        for (String token : rejected) {
            postCode(account, key, "Bearer " + token).andExpect(status().isUnauthorized());
        }
        postCode(account, key, "Bearer not-a-jwt").andExpect(status().isUnauthorized());
        assertThat(messageCount()).isZero();
    }

    @Test
    void scopeIsRequiredPerEndpoint() throws Exception {
        UUID account = newAccount();

        postCode(account, UUID.randomUUID(), bearer(account, ERASE_SCOPE)).andExpect(status().isForbidden());
        postCode(account, UUID.randomUUID(), bearer(account, "events:ingest")).andExpect(status().isForbidden());
        assertThat(messageCount()).isZero();
    }

    @Test
    void tokenBoundToAnotherAccountIsForbidden() throws Exception {
        UUID account = newAccount();

        postCode(account, UUID.randomUUID(), bearer(newAccount(), MAIL_SEND_SCOPE)).andExpect(status().isForbidden());
        postCode(account, UUID.randomUUID(),
                "Bearer " + serviceToken(account, MAIL_SEND_SCOPE, claims -> claims.claim("account_id", null)))
                .andExpect(status().isForbidden());
        assertThat(messageCount()).isZero();
    }

    @Test
    void nothingElseIsExposed() throws Exception {
        mockMvc.perform(get("/api/v1/mail/messages")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/v1/mail/verification-codes").header(HttpHeaders.AUTHORIZATION,
                bearer(newAccount(), MAIL_SEND_SCOPE))).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
