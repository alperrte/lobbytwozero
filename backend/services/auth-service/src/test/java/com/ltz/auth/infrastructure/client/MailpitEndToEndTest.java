package com.ltz.auth.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.ltz.auth.ConcurrentRequests;
import com.ltz.auth.IntegrationTestSupport;

/**
 * dev profili + gerçek Mailpit (Compose ile aynı sabit sürüm): kayıt → SMTP → Mailpit → kod → ACTIVE.
 * Mailpit dışarıya e-posta göndermez; bu test production teslimini kanıtlamaz.
 */
@ActiveProfiles("dev")
@ExtendWith(OutputCaptureExtension.class)
class MailpitEndToEndTest extends IntegrationTestSupport {

    private static final String MAILPIT_IMAGE = "axllent/mailpit:v1.31.0";
    private static final int SMTP_PORT = 1025;
    private static final int HTTP_PORT = 8025;
    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(15);
    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };

    private static final GenericContainer<?> MAILPIT = new GenericContainer<>(MAILPIT_IMAGE)
            .withExposedPorts(SMTP_PORT, HTTP_PORT)
            .waitingFor(Wait.forHttp("/api/v1/info").forPort(HTTP_PORT));

    static {
        MAILPIT.start();
    }

    private final RestClient mailpitApi = RestClient.create(
            "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(HTTP_PORT));

    @DynamicPropertySource
    static void mailpitProperties(DynamicPropertyRegistry registry) {
        registry.add("ltz.auth.mail.dev-smtp.host", MAILPIT::getHost);
        registry.add("ltz.auth.mail.dev-smtp.port", () -> MAILPIT.getMappedPort(SMTP_PORT));
    }

    @Test
    void kayitMailpiteUlasirKodHesabiAktiflestirirIkinciKullanimReddedilir() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername(), VALID_PASSWORD).andExpect(status().isAccepted());

        Map<String, Object> message = awaitMessages(email, 1).getFirst();
        assertThat(message).containsEntry("Subject", DevSmtpVerificationMailAdapter.SUBJECT);
        String code = extractCode(message);
        UUID accountId = accountId(email);
        assertThat(jdbc.queryForObject("select code_hash from email_verification_codes where account_id = ?",
                String.class, accountId)).isNotEqualTo(code).hasSize(64);
        assertThat(jdbc.queryForObject("""
                select count(*) from email_verification_codes
                where account_id = ? and invalidation_reason = 'DELIVERY_FAILED'
                """, Integer.class, accountId)).isZero();

        confirm(email, code).andExpect(status().isNoContent());
        assertThat(accountStatus(email)).isEqualTo("ACTIVE");
        confirm(email, code).andExpect(status().isBadRequest());
    }

    @Test
    void yenidenGondermeMailpiteYeniKodGonderirEskiKodGecersizdir() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername(), VALID_PASSWORD).andExpect(status().isAccepted());
        String oldCode = extractCode(awaitMessages(email, 1).getFirst());
        shiftCodesBack(accountId(email), "2 minutes");

        resend(email).andExpect(status().isAccepted());
        List<Map<String, Object>> messages = awaitMessages(email, 2);
        String newCode = messages.stream().map(this::extractCode).filter(code -> !code.equals(oldCode))
                .findFirst().orElse(oldCode);

        if (!newCode.equals(oldCode)) {
            confirm(email, oldCode).andExpect(status().isBadRequest());
        }
        confirm(email, newCode).andExpect(status().isNoContent());
    }

    @Test
    void beklemeSuresindeYenidenGondermeYeniEPostaUretmez() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername(), VALID_PASSWORD).andExpect(status().isAccepted());
        awaitMessages(email, 1);

        resend(email).andExpect(status().isAccepted());

        assertThat(securityEventCount(accountId(email), "VERIFICATION_CODE_RESEND_THROTTLED")).isEqualTo(1);
        assertThat(searchMessages(email)).hasSize(1);
    }

    @Test
    void mailpitKoduylaEszamanliIkiDogrulamadanYalnizBiriBasarili() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername(), VALID_PASSWORD).andExpect(status().isAccepted());
        String code = extractCode(awaitMessages(email, 1).getFirst());

        List<Integer> statuses = ConcurrentRequests.run(2,
                index -> () -> confirm(email, code).andReturn().getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(204, 400);
    }

    @Test
    void kodVeAliciAdresiUygulamaLoglarinaYazilmaz(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername(), VALID_PASSWORD).andExpect(status().isAccepted());
        String code = extractCode(awaitMessages(email, 1).getFirst());
        confirm(email, code).andExpect(status().isNoContent());

        assertThat(output.getAll()).doesNotContainIgnoringCase(email).doesNotContain(VALID_PASSWORD)
                .doesNotContain("kodunuz: " + code);
    }

    private List<Map<String, Object>> awaitMessages(String email, int expected) {
        await().atMost(DELIVERY_TIMEOUT).until(() -> searchMessages(email).size() >= expected);
        return searchMessages(email).stream()
                .map(summary -> mailpitApi.get().uri("/api/v1/message/{id}", summary.get("ID"))
                        .retrieve().body(JSON_OBJECT))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> searchMessages(String email) {
        Map<String, Object> result = mailpitApi.get().uri("/api/v1/search?query={query}", "to:" + email)
                .retrieve().body(JSON_OBJECT);
        return (List<Map<String, Object>>) result.get("messages");
    }

    private String extractCode(Map<String, Object> message) {
        Matcher matcher = CODE.matcher(String.valueOf(message.get("Text")));
        assertThat(matcher.find()).as("verification code in Mailpit message").isTrue();
        return matcher.group(1);
    }
}
