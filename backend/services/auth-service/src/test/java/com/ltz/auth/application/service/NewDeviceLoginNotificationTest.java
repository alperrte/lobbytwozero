package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;
import com.ltz.auth.RecordingMailPortConfiguration.RecordingMailPort;
import com.ltz.auth.api.controller.AuthApiPaths;
import com.ltz.auth.application.port.SecurityNotificationMail;
import com.ltz.auth.application.port.SecurityNotificationType;
import com.ltz.auth.infrastructure.persistence.repository.KnownDeviceRepository;

import jakarta.servlet.http.Cookie;

/**
 * "Yeni cihazdan giriş" bildirimi: ilk giriş sessiz, tanınan cihaz sessiz, yeni cihaz mail üretir; hesap başına 24 saatte
 * en çok bir mail; cihaz çerezi HttpOnly ve yalnız özeti saklanır; başarısız giriş cihaz kaydetmez.
 */
@Import(RecordingMailPortConfiguration.class)
class NewDeviceLoginNotificationTest extends IntegrationTestSupport {

    private static final String DEVICE_COOKIE = "__Host-ltz_device";

    @Autowired
    private RecordingMailPort mailPort;

    @Autowired
    private KnownDeviceRepository knownDevices;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void ilkGirisVeTaninanCihazSessizYeniCihazMailUretir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID account = accountId(email);
        String first = newToken();

        loginWith(email, VALID_PASSWORD, first).andExpect(status().isOk());
        loginWith(email, VALID_PASSWORD, first).andExpect(status().isOk());
        assertThat(deviceCount(account)).as("aynı cihaz tek kayıt").isEqualTo(1);
        assertThat(suspicious(email)).as("ilk giriş ve tanınan cihaz mail üretmez").isEmpty();

        loginWith(email, VALID_PASSWORD, newToken()).andExpect(status().isOk());

        SecurityNotificationMail mail = mailPort.awaitNotification(email, SecurityNotificationType.SUSPICIOUS_LOGIN);
        assertThat(mail.accountId()).isEqualTo(account);
        assertThat(mail.toString()).doesNotContain(email).doesNotContain(first);
        assertThat(deviceCount(account)).isEqualTo(2);
    }

    @Test
    void ayniHesapIcin24SaatteEnCokBirMailGiderSonraTekrarGider() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID account = accountId(email);
        loginWith(email, VALID_PASSWORD, newToken()).andExpect(status().isOk());

        loginWith(email, VALID_PASSWORD, newToken()).andExpect(status().isOk());
        mailPort.awaitNotification(email, SecurityNotificationType.SUSPICIOUS_LOGIN);
        loginWith(email, VALID_PASSWORD, newToken()).andExpect(status().isOk());
        assertThat(suspicious(email)).as("24 saat içinde ikinci mail yok").hasSize(1);
        assertThat(deviceCount(account)).isEqualTo(3);

        jdbc.update("update known_devices set notified_at = ? where account_id = ? and notified_at is not null",
                Timestamp.from(Instant.now().minusSeconds(25 * 3600L)), account);
        loginWith(email, VALID_PASSWORD, newToken()).andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(10))
                .until(() -> suspicious(email).size() == 2);
    }

    @Test
    void cihazCerezHttpOnlyVerilirGecersizDegerYenisiyleDegisirOzetSaklanir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID account = accountId(email);

        MvcResult fresh = login(email, VALID_PASSWORD).andExpect(status().isOk()).andReturn();
        String cookieHeader = deviceCookieHeader(fresh);
        assertThat(cookieHeader).contains("HttpOnly").contains("Secure").contains("SameSite=Lax").contains("Path=/");
        String issued = fresh.getResponse().getCookie(DEVICE_COOKIE).getValue();
        assertThat(issued).matches("^[A-Za-z0-9_-]{43}$");

        MvcResult replaced = loginWith(email, VALID_PASSWORD, "kisa-ve-gecersiz").andExpect(status().isOk())
                .andReturn();
        String replacement = replaced.getResponse().getCookie(DEVICE_COOKIE).getValue();
        assertThat(replacement).matches("^[A-Za-z0-9_-]{43}$").isNotEqualTo("kisa-ve-gecersiz");

        List<String> hashes = jdbc.queryForList("select device_hash from known_devices where account_id = ?",
                String.class, account);
        assertThat(hashes).hasSize(2).allMatch(hash -> hash.matches("^[0-9a-f]{64}$"))
                .doesNotContain(issued, replacement);
    }

    @Test
    void basarisizGirisCihazKaydetmezVeMailUretmez() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID account = accountId(email);
        loginWith(email, VALID_PASSWORD, newToken()).andExpect(status().isOk());

        MvcResult failed = loginWith(email, "yanlis-parola-yanlis-parola", newToken())
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(failed.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).as("başarısız girişte çerez verilmez")
                .isEmpty();

        assertThat(deviceCount(account)).isEqualTo(1);
        assertThat(suspicious(email)).isEmpty();
    }

    @Test
    void hesapBasinaEnCok20CihazTutulurEnEskiBudanir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID account = accountId(email);
        Instant base = Instant.now().minusSeconds(3600);
        for (int i = 0; i < 25; i++) {
            Instant seen = base.plusSeconds(i);
            jdbc.update("insert into known_devices (id, account_id, device_hash, first_seen_at, last_seen_at) "
                    + "values (?, ?, ?, ?, ?)", UUID.randomUUID(), account, "%064x".formatted(i),
                    Timestamp.from(seen), Timestamp.from(seen));
        }

        transactions.executeWithoutResult(status -> knownDevices.pruneBeyond(account, 20));

        assertThat(deviceCount(account)).isEqualTo(20);
        assertThat(jdbc.queryForObject("select count(*) from known_devices where account_id = ? and device_hash = ?",
                Integer.class, account, "%064x".formatted(0))).as("en eski silindi").isZero();
        assertThat(jdbc.queryForObject("select count(*) from known_devices where account_id = ? and device_hash = ?",
                Integer.class, account, "%064x".formatted(24))).as("en yeni kaldı").isEqualTo(1);
    }

    @Test
    void ayniCihazIkiKezEklenirseTekKayitKalir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID account = accountId(email);
        String hash = "%064x".formatted(7);

        int first = transactions.execute(status ->
                knownDevices.insertIfAbsent(UUID.randomUUID(), account, hash, Instant.now()));
        int second = transactions.execute(status ->
                knownDevices.insertIfAbsent(UUID.randomUUID(), account, hash, Instant.now()));

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(deviceCount(account)).isEqualTo(1);
    }

    private ResultActions loginWith(String email, String password, String token)
            throws Exception {
        return mockMvc.perform(post(AuthApiPaths.LOGIN).with(xsrf()).cookie(new Cookie(DEVICE_COOKIE, token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private static String deviceCookieHeader(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(DEVICE_COOKIE + "=")).findFirst().orElseThrow();
    }

    private static String newToken() {
        return (UUID.randomUUID().toString().replace("-", "") + "abcdefghijklmnop").substring(0, 43);
    }

    private int deviceCount(UUID account) {
        return jdbc.queryForObject("select count(*) from known_devices where account_id = ?", Integer.class, account);
    }

    private List<SecurityNotificationMail> suspicious(String email) {
        return mailPort.notificationsTo(email).stream()
                .filter(mail -> mail.type() == SecurityNotificationType.SUSPICIOUS_LOGIN).toList();
    }
}
