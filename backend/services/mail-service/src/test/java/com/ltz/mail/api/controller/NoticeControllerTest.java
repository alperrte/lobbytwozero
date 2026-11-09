package com.ltz.mail.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.mail.IntegrationTestSupport;

/** Platform kapısı ({@code POST /internal/v1/mail/notices}): yetki, doğrulama, idempotency, hız sınırı, saklama. */
class NoticeControllerTest extends IntegrationTestSupport {

    private ResultActions send(String authorization, String body) throws Exception {
        var request = post(InternalApiPaths.NOTICES).contentType(MediaType.APPLICATION_JSON).content(body);
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request);
    }

    private ResultActions postNotice(UUID key, UUID account, String title, String message) throws Exception {
        return send(platformBearer(), noticeBody(key, account, title, message));
    }

    private static String body(UUID key, UUID account, String extraFields) {
        return """
                {"idempotencyKey":"%s","accountId":"%s","kind":"INFO","title":"Başlık","message":"Mesaj"%s}"""
                .formatted(key, account, extraFields);
    }

    // ---------------------------------------------------------------- kabul ve saklama

    @Test
    void noticeIsQueuedWithoutStoringAnyRecipientAddress() throws Exception {
        UUID account = newAccount();
        UUID key = UUID.randomUUID();

        postNotice(key, account, "Arkadaşlık isteği", "Ahmet sana arkadaşlık isteği gönderdi.")
                .andExpect(status().isAccepted());

        Map<String, Object> row = messageByKey(key);
        assertThat(row).containsEntry("status", "PENDING").containsEntry("type", "NOTICE")
                .containsEntry("caller", PLATFORM_CALLER).containsEntry("recipient_user_id", account)
                .containsEntry("recipient_masked", "***").containsEntry("locale", "tr");
        assertThat(row.get("recipient_email")).isNull();
        assertThat(row.get("payload_encrypted")).isNotNull();
        // Düz içerik kalıcı yazılmaz: şifreli blobta başlık görünmemeli.
        assertThat(new String((byte[]) row.get("payload_encrypted"), java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("Arkadaşlık").doesNotContain("Ahmet");
        assertThat(resolver.calls()).as("adres istekte değil gönderim anında çözülür").isZero();
    }

    @Test
    void actionButtonWithAnAllowedLinkIsAccepted() throws Exception {
        UUID account = newAccount();
        UUID key = UUID.randomUUID();
        String extra = ",\"actionLabel\":\"İsteği gör\",\"actionLink\":\"" + RESET_LINK + "\",\"locale\":\"en\"";

        send(platformBearer(), body(key, account, extra)).andExpect(status().isAccepted());

        assertThat(messageByKey(key)).containsEntry("locale", "en");
    }

    // ---------------------------------------------------------------- kimlik ve yetki

    @Test
    void registeredCallerWithoutAnyTokenOrWithAUserTokenIsRejected() throws Exception {
        UUID account = newAccount();
        String body = noticeBody(UUID.randomUUID(), account, "T", "M");

        send(null, body).andExpect(status().isForbidden());
        send("Bearer " + userAccessToken(account), body).andExpect(status().isUnauthorized());
        assertThat(messageCount()).isZero();
    }

    @Test
    void unregisteredServiceTokenIsRejectedEvenWithAValidSignatureAndScope() throws Exception {
        String body = noticeBody(UUID.randomUUID(), newAccount(), "T", "M");

        send(platformBearer("svc:unknown-service", MAIL_NOTIFY_SCOPE), body).andExpect(status().isUnauthorized());
        assertThat(messageCount()).isZero();
    }

    @Test
    void authCannotUseThePlatformGateAndPlatformCallersCannotUseTheAuthGate() throws Exception {
        UUID account = newAccount();
        String body = noticeBody(UUID.randomUUID(), account, "T", "M");

        // Auth, mail:send kapsamıyla bu uca giremez; mail:notify kapsamlı Auth token'ı da kayıtlı çağıran değildir.
        send(bearer(account, MAIL_SEND_SCOPE), body).andExpect(status().isForbidden());
        send(platformBearer(AUTH_SUBJECT, MAIL_NOTIFY_SCOPE), body).andExpect(status().isForbidden());
        // Platform çağıranı Auth'un uçlarına giremez: mail:send kapsamı taşısa bile token geçerli olsa da kapı Auth'a özeldir.
        mockMvc.perform(post(InternalApiPaths.VERIFICATION_CODES).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, platformBearer(PLATFORM_SUBJECT, MAIL_SEND_SCOPE))
                        .content(verificationBody(UUID.randomUUID(), account, "a@b.com", "123456")))
                .andExpect(status().isForbidden());
        // Platform çağıranı Auth'un uçlarına giremez (kapsam farklı).
        mockMvc.perform(post(InternalApiPaths.SECURITY_NOTIFICATIONS).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, platformBearer())
                        .content(notificationBody(UUID.randomUUID(), account, "a@b.com", "PASSWORD_CHANGED")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(InternalApiPaths.ACCOUNT_ERASURE, account)
                        .header(HttpHeaders.AUTHORIZATION, platformBearer()))
                .andExpect(status().isForbidden());
        assertThat(messageCount()).isZero();
    }

    @Test
    void platformGateIsClosedWhileAuthClientIsNotConfigured() throws Exception {
        resolver.setConfigured(false);

        postNotice(UUID.randomUUID(), newAccount(), "T", "M").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PLATFORM_NOT_CONFIGURED"));
        assertThat(messageCount()).isZero();
    }

    // ---------------------------------------------------------------- içerik doğrulaması

    @Test
    void invalidContentIsRejectedWithoutQueueingAnything() throws Exception {
        UUID account = newAccount();
        String longTitle = "a".repeat(81);
        String longMessage = "a".repeat(601);

        postNotice(UUID.randomUUID(), account, "", "M").andExpect(status().isBadRequest());
        postNotice(UUID.randomUUID(), account, "T", "").andExpect(status().isBadRequest());
        postNotice(UUID.randomUUID(), account, longTitle, "M").andExpect(status().isBadRequest());
        postNotice(UUID.randomUUID(), account, "T", longMessage).andExpect(status().isBadRequest());
        send(platformBearer(), body(UUID.randomUUID(), account, "").replace("\"INFO\"", "\"LOUD\""))
                .andExpect(status().isBadRequest());
        send(platformBearer(), body(UUID.randomUUID(), account, ",\"recipientEmail\":\"x@y.com\""))
                .andExpect(status().isBadRequest());
        assertThat(messageCount()).isZero();
    }

    @Test
    void controlAndDirectionOverrideCharactersAreRejected() throws Exception {
        UUID account = newAccount();

        postNotice(UUID.randomUUID(), account, "Başlık\\nİkinci satır", "M")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CONTENT"));
        postNotice(UUID.randomUUID(), account, "T", "Gizli \\u202E ters")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CONTENT"));
        // Mesajda satır sonu serbesttir (paragraflar).
        postNotice(UUID.randomUUID(), account, "T", "Birinci\\n\\nİkinci").andExpect(status().isAccepted());
        assertThat(messageCount()).isEqualTo(1);
    }

    @Test
    void actionLabelAndLinkMustComeTogetherAndLinkMustBeAllowed() throws Exception {
        UUID account = newAccount();

        send(platformBearer(), body(UUID.randomUUID(), account, ",\"actionLabel\":\"Aç\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CONTENT"));
        send(platformBearer(), body(UUID.randomUUID(), account, ",\"actionLink\":\"" + RESET_LINK + "\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CONTENT"));
        send(platformBearer(), body(UUID.randomUUID(), account,
                ",\"actionLabel\":\"Aç\",\"actionLink\":\"https://evil.example/x\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_LINK"));
        send(platformBearer(), body(UUID.randomUUID(), account,
                ",\"actionLabel\":\"Aç\",\"actionLink\":\"http://" + ALLOWED_LINK_HOST + "/x\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_LINK"));
        assertThat(messageCount()).isZero();
    }

    // ---------------------------------------------------------------- idempotency ve hız sınırı

    @Test
    void sameKeyAndContentIsAcceptedOnceAndDifferentContentConflicts() throws Exception {
        UUID account = newAccount();
        UUID key = UUID.randomUUID();

        postNotice(key, account, "T", "M").andExpect(status().isAccepted());
        postNotice(key, account, "T", "M").andExpect(status().isAccepted());
        postNotice(key, account, "T", "Başka").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        assertThat(messageCount()).isEqualTo(1);
    }

    @Test
    void accountRateLimitAppliesPerCallerAndAccountButNotToRepeats() throws Exception {
        UUID account = newAccount();
        UUID repeated = UUID.randomUUID();
        postNotice(repeated, account, "T", "M").andExpect(status().isAccepted());
        postNotice(UUID.randomUUID(), account, "T", "M").andExpect(status().isAccepted());
        postNotice(UUID.randomUUID(), account, "T", "M").andExpect(status().isAccepted());

        postNotice(UUID.randomUUID(), account, "T", "M").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        // Sınırdayken aynı isteğin tekrarı yeni kayıt sayılmaz, kabul edilir.
        postNotice(repeated, account, "T", "M").andExpect(status().isAccepted());
        // Başka hesap etkilenmez.
        postNotice(UUID.randomUUID(), newAccount(), "T", "M").andExpect(status().isAccepted());

        assertThat(messageCount()).isEqualTo(4);
    }

    @Test
    void callerRateLimitCapsTheTotalAcrossAccounts() throws Exception {
        for (int index = 0; index < 6; index++) {
            postNotice(UUID.randomUUID(), newAccount(), "T", "M").andExpect(status().isAccepted());
        }

        postNotice(UUID.randomUUID(), newAccount(), "T", "M").andExpect(status().isTooManyRequests());
        assertThat(messageCount()).isEqualTo(6);
    }

    // ---------------------------------------------------------------- hesap silme

    @Test
    void accountErasureAlsoRemovesPlatformNotices() throws Exception {
        UUID account = newAccount();
        UUID key = UUID.randomUUID();
        postNotice(key, account, "T", "M").andExpect(status().isAccepted());

        mockMvc.perform(delete(InternalApiPaths.ACCOUNT_ERASURE, account)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account, ERASE_SCOPE)))
                .andExpect(status().isNoContent());

        assertThat(messageCount()).isZero();
    }
}
