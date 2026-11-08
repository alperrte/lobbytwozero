package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;
import com.ltz.auth.RecordingMailPortConfiguration.RecordingMailPort;
import com.ltz.auth.application.port.SecurityNotificationType;
import com.ltz.auth.application.service.SuspensionExpiryService;

/**
 * Süreli hesap askısı (B8): bitiş tarihi doğrulaması, süre dolunca otomatik ACTIVE, süresiz askı ve yasağın otomatik
 * açılmaması, elle açmada bitişin temizlenmesi. Arka plan işi testlerde kapalıdır; bitiş servisi elle tetiklenir.
 */
@Import(RecordingMailPortConfiguration.class)
class SuspensionExpiryTest extends IntegrationTestSupport {

    private static final String REASON = "Topluluk kurallarının ihlali (test)";

    @Autowired
    private SuspensionExpiryService expiryService;

    @Autowired
    private RecordingMailPort mailPort;

    @Test
    void suureliAskiBitisiGorunurErkenAcilmazSureDolunca() throws Exception {
        String bearer = bearerFor(newAdmin());
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID account = accountId(email);
        Instant until = Instant.now().plus(Duration.ofHours(2));

        suspend(bearer, account, until).andExpect(status().isNoContent());

        assertThat(accountStatus(account)).isEqualTo("SUSPENDED");
        assertThat(suspendedUntil(account)).isNotNull();
        login(email, VALID_PASSWORD).andExpect(status().isForbidden());
        mockMvc.perform(get(AuthApiPaths.ADMIN + "/accounts/" + account).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspendedUntil").exists());

        // Süre dolmadan arka plan işi hesabı açmaz.
        assertThat(expiryService.expireDue()).isZero();
        assertThat(accountStatus(account)).isEqualTo("SUSPENDED");

        // Süre dolar (saat ileri alınmak yerine bitiş geçmişe çekilir; veritabanı kısıtı SUSPENDED için buna izin verir).
        jdbc.update("update accounts set suspended_until = now() - interval '1 minute' where id = ?", account);

        assertThat(expiryService.expireDue()).isEqualTo(1);
        assertThat(accountStatus(account)).isEqualTo("ACTIVE");
        assertThat(suspendedUntil(account)).isNull();
        assertThat(securityEventCount(account, "ACCOUNT_STATUS_CHANGED")).isEqualTo(2);
        Map<String, Object> history = jdbc.queryForList("select to_status, actor_id, reason from account_status_history "
                + "where account_id = ? order by created_at desc", account).getFirst();
        assertThat(history).containsEntry("to_status", "ACTIVE")
                .containsEntry("actor_id", SuspensionExpiryService.SYSTEM_ACTOR)
                .containsEntry("reason", "Süreli askı süresi doldu");
        assertThat(mailPort.awaitNotification(email, SecurityNotificationType.ACCOUNT_REACTIVATED)).isNotNull();
        login(email, VALID_PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AUTHENTICATED"));

        // İkinci tur aynı hesabı tekrar işlemez.
        assertThat(expiryService.expireDue()).isZero();
    }

    @Test
    void suresizAskiVeYasakOtomatikAcilmaz() throws Exception {
        String bearer = bearerFor(newAdmin());
        UUID indefinite = accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
        UUID banned = accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));

        admin(bearer, indefinite, "{\"status\":\"SUSPENDED\",\"reason\":\"%s\"}".formatted(REASON))
                .andExpect(status().isNoContent());
        admin(bearer, banned, "{\"status\":\"BANNED\",\"reason\":\"%s\"}".formatted(REASON))
                .andExpect(status().isNoContent());

        assertThat(suspendedUntil(indefinite)).isNull();
        assertThat(expiryService.expireDue()).isZero();
        assertThat(accountStatus(indefinite)).isEqualTo("SUSPENDED");
        assertThat(accountStatus(banned)).isEqualTo("BANNED");
    }

    @Test
    void gecersizBitisTarihleriReddedilir() throws Exception {
        String bearer = bearerFor(newAdmin());
        UUID account = accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));

        // Geçmiş, çok yakın ve izin verilen en uzundan (365 gün) sonraki tarih.
        for (Instant bad : List.of(Instant.now().minus(Duration.ofHours(1)), Instant.now().plusSeconds(10),
                Instant.now().plus(Duration.ofDays(400)))) {
            suspend(bearer, account, bad).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SUSPENSION_END"));
        }
        // Bitiş yalnız SUSPENDED için verilebilir.
        admin(bearer, account, "{\"status\":\"BANNED\",\"reason\":\"%s\",\"suspendedUntil\":\"%s\"}"
                .formatted(REASON, Instant.now().plus(Duration.ofDays(1)))).andExpect(status().isBadRequest());
        assertThat(accountStatus(account)).isEqualTo("ACTIVE");
        assertThat(suspendedUntil(account)).isNull();
    }

    @Test
    void elleAcmaVeYasaklamaBitisiTemizler() throws Exception {
        String bearer = bearerFor(newAdmin());
        UUID reopened = accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
        UUID banned = accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
        Instant until = Instant.now().plus(Duration.ofDays(3));
        suspend(bearer, reopened, until).andExpect(status().isNoContent());
        suspend(bearer, banned, until).andExpect(status().isNoContent());

        admin(bearer, reopened, "{\"status\":\"ACTIVE\",\"reason\":\"%s\"}".formatted(REASON))
                .andExpect(status().isNoContent());
        admin(bearer, banned, "{\"status\":\"BANNED\",\"reason\":\"%s\"}".formatted(REASON))
                .andExpect(status().isNoContent());

        assertThat(accountStatus(reopened)).isEqualTo("ACTIVE");
        assertThat(suspendedUntil(reopened)).isNull();
        assertThat(accountStatus(banned)).isEqualTo("BANNED");
        assertThat(suspendedUntil(banned)).as("yasak süresizdir, eski askı bitişi taşınmaz").isNull();
        assertThat(expiryService.expireDue()).isZero();
    }

    // ---- Yardımcılar ----

    private UUID newAdmin() throws Exception {
        UUID admin = accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
        enableTotp(admin);
        grantRole(admin, "ADMIN");
        return admin;
    }

    private ResultActions suspend(String bearer, UUID account, Instant until) throws Exception {
        return admin(bearer, account, "{\"status\":\"SUSPENDED\",\"reason\":\"%s\",\"suspendedUntil\":\"%s\"}"
                .formatted(REASON, until));
    }

    private ResultActions admin(String bearer, UUID account, String json) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.ADMIN + "/accounts/" + account + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String accountStatus(UUID account) {
        return jdbc.queryForObject("select status from accounts where id = ?", String.class, account);
    }

    private Object suspendedUntil(UUID account) {
        return jdbc.queryForObject("select suspended_until from accounts where id = ?", Object.class, account);
    }
}
