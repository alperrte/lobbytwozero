package com.ltz.mail.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.ltz.mail.IntegrationTestSupport;
import com.ltz.mail.api.controller.InternalApiPaths;
import com.ltz.mail.application.port.MailDeliveryException;
import com.ltz.mail.application.port.OutgoingMail;

/** Platform kapısı: adres gönderim anında Auth'tan çözülür; gönderime uygun olmayan alıcı atlanır, Auth kapalıysa kayıt bekler. */
class NoticeDispatchTest extends IntegrationTestSupport {

    private static final String EMAIL = "kerem@example.com";

    @Autowired
    private MailDispatchService dispatch;

    private UUID queue(UUID account, String title, String message) throws Exception {
        UUID key = UUID.randomUUID();
        mockMvc.perform(post(InternalApiPaths.NOTICES).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, platformBearer())
                        .content(noticeBody(key, account, title, message)))
                .andExpect(status().isAccepted());
        return key;
    }

    @Test
    void verifiedActiveAccountGetsTheMailAtTheAddressResolvedAtSendTime() throws Exception {
        UUID account = newAccount();
        resolver.givenAccount(account, EMAIL, true, "ACTIVE");
        UUID key = queue(account, "Arkadaşlık isteği", "Ahmet sana arkadaşlık isteği gönderdi.");

        assertThat(dispatch.dispatchDue()).isEqualTo(1);

        assertThat(mailSender.sent()).hasSize(1);
        OutgoingMail mail = mailSender.sent().getFirst();
        assertThat(mail.to()).isEqualTo(EMAIL);
        assertThat(mail.subject()).isEqualTo("Arkadaşlık isteği");
        assertThat(mail.html()).contains("BİLGİLENDİRME", "Arkadaşlık isteği", "Ahmet sana arkadaşlık isteği gönderdi.",
                "cid:ltz-logo");
        assertThat(mail.text()).contains("Ahmet sana arkadaşlık isteği gönderdi.");
        assertThat(resolver.calls()).isEqualTo(1);
        // Adres hiçbir aşamada kalıcı yazılmaz; gönderimden sonra da kayıtta yoktur.
        Map<String, Object> row = messageByKey(key);
        assertThat(row).containsEntry("status", "SENT");
        assertThat(row.get("recipient_email")).isNull();
        assertThat(row.get("payload_encrypted")).isNull();
    }

    @Test
    void contentIsEscapedInHtmlAndTheButtonCarriesTheAllowedLink() throws Exception {
        UUID account = newAccount();
        resolver.givenAccount(account, EMAIL, true, "ACTIVE");
        UUID key = UUID.randomUUID();
        String body = """
                {"idempotencyKey":"%s","accountId":"%s","kind":"DANGER","title":"Dikkat <b>önemli</b>",
                 "message":"Birinci paragraf\\n\\n<script>alert(1)</script>","actionLabel":"Aç",
                 "actionLink":"%s","locale":"en"}""".formatted(key, account, RESET_LINK);
        mockMvc.perform(post(InternalApiPaths.NOTICES).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, platformBearer()).content(body))
                .andExpect(status().isAccepted());

        dispatch.dispatchDue();

        OutgoingMail mail = mailSender.sent().getFirst();
        assertThat(mail.html()).contains("IMPORTANT", "&lt;b&gt;önemli&lt;/b&gt;", "&lt;script&gt;alert(1)&lt;/script&gt;",
                "href=\"" + RESET_LINK + "\"").doesNotContain("<script>").doesNotContain("<b>")
                .contains("<html lang=\"en\"");
        assertThat(mail.text()).contains("<script>alert(1)</script>", "Aç: " + RESET_LINK);
    }

    @Test
    void recipientsThatCannotReceiveAreSkippedPermanentlyAndNothingIsSent() throws Exception {
        UUID noEmail = newAccount();
        UUID unverified = newAccount();
        UUID suspended = newAccount();
        UUID unknown = newAccount();
        resolver.givenAccount(noEmail, null, false, "ACTIVE");
        resolver.givenAccount(unverified, EMAIL, false, "ACTIVE");
        resolver.givenAccount(suspended, EMAIL, true, "SUSPENDED");
        UUID k1 = queue(noEmail, "T", "M");
        UUID k2 = queue(unverified, "T", "M");
        UUID k3 = queue(suspended, "T", "M");
        UUID k4 = queue(unknown, "T", "M");

        assertThat(dispatch.dispatchDue()).isEqualTo(4);

        assertThat(mailSender.sent()).isEmpty();
        assertThat(messageByKey(k1)).containsEntry("status", "FAILED").containsEntry("last_error_code", "NO_EMAIL");
        assertThat(messageByKey(k2)).containsEntry("status", "FAILED").containsEntry("last_error_code", "EMAIL_NOT_VERIFIED");
        assertThat(messageByKey(k3)).containsEntry("status", "FAILED").containsEntry("last_error_code", "ACCOUNT_NOT_ACTIVE");
        assertThat(messageByKey(k4)).containsEntry("status", "FAILED").containsEntry("last_error_code", "RECIPIENT_NOT_FOUND");
    }

    @Test
    void authOutageKeepsTheMailQueuedAndItIsSentOnceAuthIsBack() throws Exception {
        UUID account = newAccount();
        resolver.givenAccount(account, EMAIL, true, "ACTIVE");
        UUID key = queue(account, "T", "M");
        resolver.failNext(MailDeliveryException.transientFailure("RESOLVER_UNAVAILABLE", null));

        dispatch.dispatchDue();

        assertThat(mailSender.sent()).isEmpty();
        assertThat(messageByKey(key)).containsEntry("status", "PENDING").containsEntry("last_error_code", "RESOLVER_UNAVAILABLE")
                .containsEntry("attempts", 1);

        makeDue(key);
        dispatch.dispatchDue();

        assertThat(mailSender.sent()).hasSize(1);
        assertThat(messageByKey(key)).containsEntry("status", "SENT");
    }

    @Test
    void theAuthGateStillUsesTheAddressFromTheRequestAndNeverCallsTheResolver() throws Exception {
        UUID account = newAccount();
        UUID key = UUID.randomUUID();
        mockMvc.perform(post(InternalApiPaths.VERIFICATION_CODES).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account, MAIL_SEND_SCOPE))
                        .content(verificationBody(key, account, EMAIL, "123456")))
                .andExpect(status().isAccepted());

        dispatch.dispatchDue();

        assertThat(mailSender.sent()).hasSize(1);
        assertThat(mailSender.sent().getFirst().to()).isEqualTo(EMAIL);
        assertThat(resolver.calls()).isZero();
    }
}
