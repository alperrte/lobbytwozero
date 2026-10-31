package com.ltz.auth.infrastructure.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.ltz.auth.application.port.AccountDeletionMail;
import com.ltz.auth.application.port.AccountDeletionMailPort;
import com.ltz.auth.application.port.PasswordResetMail;
import com.ltz.auth.application.port.PasswordResetMailPort;
import com.ltz.auth.application.port.SecurityNotificationMail;
import com.ltz.auth.application.port.SecurityNotificationMailPort;
import com.ltz.auth.application.port.VerificationCodeDeliveryException;
import com.ltz.auth.application.port.VerificationCodeMail;
import com.ltz.auth.application.port.VerificationCodeMailPort;
import com.ltz.auth.infrastructure.config.DeletionProperties;
import com.ltz.auth.infrastructure.security.ServiceTokenIssuer;
import com.ltz.auth.infrastructure.security.TokenClaims;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Auth → Mail Service (MAIL-01..04): {@code POST <MAIL_SERVICE_URL>/internal/v1/mail/<uç>}, her çağrıda yeni servis
 * token'ıyla ({@code typ=svc+jwt}, {@code aud=mail-service}, {@code scope=mail:send}, {@code account_id}).
 * <p>
 * Teslim yalnız {@code 202} ile kabul edilmiş sayılır: Mail Service isteği <b>kalıcı kuyruğa yazdı</b> demektir
 * (e-postanın gittiği anlamına gelmez). Diğer tüm kodlar, zaman aşımı ve bağlantı hatası
 * {@link VerificationCodeDeliveryException} olur; çağıran kodu DELIVERY_FAILED yapar. Yönlendirme izlenmez (token başka
 * adrese gitmez). Yanıt gövdesi okunmaz; kod, bağlantı, token ve alıcı adresi loglanmaz. {@code locale} gönderilmez:
 * Mail Service iki dilli (TR + EN) e-posta üretir. Taban adres ve zaman aşımı hesap silme ayarlarındaki {@code mail}
 * hedefinden gelir (açılışta doğrulanır; prod'da https).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ltz.auth.mail.adapter", havingValue = "mail-service")
public class HttpMailServiceAdapter implements VerificationCodeMailPort, PasswordResetMailPort,
        AccountDeletionMailPort, SecurityNotificationMailPort {

    static final String MAIL_SERVICE = "mail";
    static final String VERIFICATION_CODES = "verification-codes";
    static final String PASSWORD_RESETS = "password-resets";
    static final String ACCOUNT_DELETIONS = "account-deletions";
    static final String SECURITY_NOTIFICATIONS = "security-notifications";
    private static final String[] MAIL_PATH_SEGMENTS = {"internal", "v1", "mail"};
    private static final String BEARER = "Bearer ";

    private final HttpClient httpClient;
    private final RestClient restClient;
    private final ServiceTokenIssuer serviceTokens;
    private final URI baseUrl;

    public HttpMailServiceAdapter(DeletionProperties properties, ServiceTokenIssuer serviceTokens) {
        this.baseUrl = properties.serviceUrls().get(MAIL_SERVICE);
        if (baseUrl == null) {
            throw new IllegalStateException(
                    "ltz.auth.deletion.service-urls.mail is required for the mail-service adapter");
        }
        Duration timeout = properties.httpTimeout();
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.serviceTokens = serviceTokens;
    }

    /** Bağlam kapanırken kendi oluşturduğu HTTP istemcisini kapatır. */
    @PreDestroy
    void close() {
        httpClient.close();
    }

    @Override
    public void sendVerificationCode(VerificationCodeMail mail) {
        Map<String, Object> body = base(mail.idempotencyKey(), mail.accountId(), mail.recipientEmail());
        body.put("code", mail.code());
        body.put("validitySeconds", mail.validity().toSeconds());
        post(VERIFICATION_CODES, mail.accountId(), body);
    }

    @Override
    public void sendPasswordReset(PasswordResetMail mail) {
        Map<String, Object> body = base(mail.idempotencyKey(), mail.accountId(), mail.recipientEmail());
        body.put("resetLink", mail.resetLink());
        body.put("validitySeconds", mail.validity().toSeconds());
        post(PASSWORD_RESETS, mail.accountId(), body);
    }

    @Override
    public void sendAccountDeletionConfirmation(AccountDeletionMail mail) {
        Map<String, Object> body = base(mail.idempotencyKey(), mail.accountId(), mail.recipientEmail());
        body.put("confirmationLink", mail.confirmationLink());
        body.put("validitySeconds", mail.validity().toSeconds());
        post(ACCOUNT_DELETIONS, mail.accountId(), body);
    }

    @Override
    public void sendSecurityNotification(SecurityNotificationMail mail) {
        Map<String, Object> body = base(mail.idempotencyKey(), mail.accountId(), mail.recipientEmail());
        body.put("type", mail.type().name());
        body.put("occurredAt", mail.occurredAt().toString());
        post(SECURITY_NOTIFICATIONS, mail.accountId(), body);
    }

    private static Map<String, Object> base(UUID idempotencyKey, UUID accountId, String recipientEmail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", idempotencyKey);
        body.put("accountId", accountId);
        body.put("recipientEmail", recipientEmail);
        return body;
    }

    private void post(String endpoint, UUID accountId, Map<String, Object> body) {
        String token = serviceTokens.issue(ServiceTokenIssuer.audienceFor(MAIL_SERVICE), TokenClaims.MAIL_SEND_SCOPE,
                accountId);
        int status;
        try {
            status = restClient.post()
                    .uri(uri(endpoint))
                    .header(HttpHeaders.AUTHORIZATION, BEARER + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchangeForRequiredValue((request, response) -> response.getStatusCode().value());
        } catch (RestClientException e) {
            log.debug("Mail Service unreachable endpoint={} cause={}", endpoint, e.getClass().getSimpleName());
            throw new VerificationCodeDeliveryException("Mail Service is unreachable", e);
        }
        if (status != HttpStatus.ACCEPTED.value()) {
            log.debug("Mail Service did not accept endpoint={} status={}", endpoint, status);
            throw new VerificationCodeDeliveryException("Mail Service rejected the request (HTTP " + status + ")");
        }
    }

    private URI uri(String endpoint) {
        return UriComponentsBuilder.newInstance()
                .scheme(baseUrl.getScheme())
                .host(baseUrl.getHost())
                .port(baseUrl.getPort())
                .pathSegment(MAIL_PATH_SEGMENTS)
                .pathSegment(endpoint)
                .build()
                .encode()
                .toUri();
    }
}
