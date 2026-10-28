package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.servlet.http.Cookie;

import com.ltz.auth.ConcurrentRequests;
import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;

@Import(RecordingMailPortConfiguration.class)
class AdminAccountTest extends IntegrationTestSupport {

    private static final String REASON = "Topluluk kurallarının ihlali (test)";

    // ---- RBAC (AUTH-14): 401 / 403 ayrımı ve yetki yükseltme ----

    @Test
    void kimliksiz401PlayerVeModeratorYoneticiUclarinaGiremez403() throws Exception {
        UUID target = newAccount();
        mockMvc.perform(get(AuthApiPaths.ADMIN_ROLES)).andExpect(status().isUnauthorized());

        UUID player = newAccount();
        UUID moderator = newAccount();
        enableTotp(moderator);
        grantRole(moderator, "MODERATOR");

        for (UUID actor : List.of(player, moderator)) {
            String bearer = bearerFor(actor);
            mockMvc.perform(get(AuthApiPaths.ADMIN_ROLES).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
            admin(bearer, target, "/status", "{\"status\":\"BANNED\",\"reason\":\"x\"}").andExpect(status().isForbidden());
            admin(bearer, target, "/roles/grant", "{\"role\":\"ADMIN\",\"reason\":\"x\"}").andExpect(status().isForbidden());
        }
        assertThat(roles(target)).containsExactly("PLAYER");
        assertThat(accountStatus(target)).isEqualTo("ACTIVE");
    }

    @Test
    void yoneticiUclariCookieIleCsrfOlmadanReddedilir() throws Exception {
        UUID admin = newAdmin();
        String access = bearerFor(admin).substring("Bearer ".length());

        mockMvc.perform(post(AuthApiPaths.ADMIN + "/accounts/" + newAccount() + "/status")
                        .cookie(new Cookie(ACCESS_COOKIE, access))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BANNED\",\"reason\":\"x\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void yoneticiRolleriListelerHesapGorunumuEpostaIcermez() throws Exception {
        String bearer = bearerFor(newAdmin());
        UUID target = newAccount();

        mockMvc.perform(get(AuthApiPaths.ADMIN_ROLES).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("PLAYER")).andExpect(jsonPath("$.length()").value(5));
        String body = mockMvc.perform(get(AuthApiPaths.ADMIN + "/accounts/" + target)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("PLAYER")).andExpect(jsonPath("$.twoFactorEnabled").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("@").doesNotContain("password");
        mockMvc.perform(get(AuthApiPaths.ADMIN + "/accounts/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isNotFound());
    }

    // ---- Hesap durumları (AUTH-12) ----

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDED", "BANNED", "DISABLED"})
    void kisitlananHesapAnindaOturumVeTokenKaybederGirisEngellenir(String target) throws Exception {
        String bearer = bearerFor(newAdmin());
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        SessionCookiePair session = loginSession(email);

        admin(bearer, accountId, "/status", "{\"status\":\"%s\",\"reason\":\"%s\"}".formatted(target, REASON))
                .andExpect(status().isNoContent());

        assertThat(accountStatus(accountId)).isEqualTo(target);
        mockMvc.perform(post(AuthApiPaths.SESSION_LOGOUT_ALL).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + session.access().getValue())).andExpect(status().isUnauthorized());
        refresh(session.refresh()).andExpect(status().isUnauthorized());
        login(email, VALID_PASSWORD).andExpect(status().isForbidden());
        assertThat(jdbc.queryForMap("select from_status, to_status, reason from account_status_history "
                + "where account_id = ?", accountId)).containsEntry("from_status", "ACTIVE")
                .containsEntry("to_status", target).containsEntry("reason", REASON);
        assertThat(securityEventCount(accountId, "ACCOUNT_STATUS_CHANGED")).isEqualTo(1);
    }

    @Test
    void askiKaldirilincaYenidenGirisYapilabilir() throws Exception {
        String bearer = bearerFor(newAdmin());
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        admin(bearer, accountId, "/status", statusJson("SUSPENDED")).andExpect(status().isNoContent());

        admin(bearer, accountId, "/status", statusJson("ACTIVE")).andExpect(status().isNoContent());

        login(email, VALID_PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AUTHENTICATED"));
    }

    @Test
    void yasakKaldirilincaYenidenGirisYapilabilir() throws Exception {
        String bearer = bearerFor(newAdmin());
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        admin(bearer, accountId, "/status", statusJson("BANNED")).andExpect(status().isNoContent());
        login(email, VALID_PASSWORD).andExpect(status().isForbidden());

        admin(bearer, accountId, "/status", statusJson("ACTIVE")).andExpect(status().isNoContent());

        login(email, VALID_PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AUTHENTICATED"));
        assertThat(jdbc.queryForList("select to_status from account_status_history where account_id = ? "
                + "order by created_at", String.class, accountId)).containsExactly("BANNED", "ACTIVE");
    }

    @Test
    void gecersizDurumGecisleriGerekcesizIstekVeKendiniDegistirmeReddedilir() throws Exception {
        UUID admin = newAdmin();
        String bearer = bearerFor(admin);
        UUID banned = newAccount();
        admin(bearer, banned, "/status", statusJson("BANNED")).andExpect(status().isNoContent());
        UUID active = newAccount();

        admin(bearer, banned, "/status", statusJson("SUSPENDED")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        admin(bearer, active, "/status", statusJson("ACTIVE")).andExpect(status().isConflict());
        admin(bearer, active, "/status", statusJson("PENDING_VERIFICATION")).andExpect(status().isConflict());
        admin(bearer, active, "/status", statusJson("DELETED")).andExpect(status().isConflict());
        admin(bearer, active, "/status", "{\"status\":\"SUSPENDED\",\"reason\":\"   \"}").andExpect(status().isBadRequest());
        admin(bearer, active, "/status", "{\"status\":\"SUSPENDED\"}").andExpect(status().isBadRequest());
        admin(bearer, active, "/status", "{\"status\":\"NOT_A_STATUS\",\"reason\":\"x\"}").andExpect(status().isBadRequest());
        admin(bearer, active, "/status", "{\"status\":\"SUSPENDED\",\"reason\":\"%s\"}".formatted("x".repeat(501)))
                .andExpect(status().isBadRequest());
        admin(bearer, admin, "/status", statusJson("SUSPENDED")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_MODIFICATION_FORBIDDEN"));
        admin(bearer, UUID.randomUUID(), "/status", statusJson("SUSPENDED")).andExpect(status().isNotFound());
        assertThat(accountStatus(active)).isEqualTo("ACTIVE");
        assertThat(accountStatus(admin)).isEqualTo("ACTIVE");
    }

    // ---- Rol yönetimi (AUTH-11) ----

    @Test
    void rolVerilirVeAlinirOturumlarIptalEdilirDenetimKaydiYazilir() throws Exception {
        UUID admin = newAdmin();
        String bearer = bearerFor(admin);
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        SessionCookiePair session = loginSession(email);

        admin(bearer, accountId, "/roles/grant", roleJson("CONTENT_CREATOR")).andExpect(status().isNoContent());

        assertThat(roles(accountId)).containsExactlyInAnyOrder("PLAYER", "CONTENT_CREATOR");
        refresh(session.refresh()).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select revoke_reason from auth_sessions where account_id = ?", String.class,
                accountId)).isEqualTo("ROLE_CHANGED");
        String token = loginSession(email).access().getValue();
        assertThat(token).isNotBlank();
        assertThat(jdbc.queryForMap("select role_code, action, reason, actor_id from account_role_history "
                + "where account_id = ?", accountId)).containsEntry("role_code", "CONTENT_CREATOR")
                .containsEntry("action", "GRANT").containsEntry("actor_id", admin);

        admin(bearer, accountId, "/roles/revoke", roleJson("CONTENT_CREATOR")).andExpect(status().isNoContent());

        assertThat(roles(accountId)).containsExactly("PLAYER");
        assertThat(securityEventCount(accountId, "ACCOUNT_ROLE_GRANTED")).isEqualTo(1);
        assertThat(securityEventCount(accountId, "ACCOUNT_ROLE_REVOKED")).isEqualTo(1);
    }

    @Test
    void zorunluRollerYalnizTotpEtkinHesabaVerilir() throws Exception {
        String bearer = bearerFor(newAdmin());
        UUID withoutTotp = newAccount();
        UUID withTotp = newAccount();
        enableTotp(withTotp);

        for (String role : List.of("MODERATOR", "ADMIN")) {
            admin(bearer, withoutTotp, "/roles/grant", roleJson(role)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROLE_REQUIRES_TWO_FACTOR"));
        }
        assertThat(roles(withoutTotp)).containsExactly("PLAYER");

        admin(bearer, withTotp, "/roles/grant", roleJson("MODERATOR")).andExpect(status().isNoContent());
        assertThat(roles(withTotp)).containsExactlyInAnyOrder("PLAYER", "MODERATOR");
    }

    @Test
    void gecersizRolIslemleriReddedilir() throws Exception {
        UUID admin = newAdmin();
        String bearer = bearerFor(admin);
        UUID target = newAccount();

        admin(bearer, target, "/roles/revoke", roleJson("PLAYER")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BASE_ROLE_REQUIRED"));
        admin(bearer, target, "/roles/revoke", roleJson("MODERATOR")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ASSIGNED"));
        admin(bearer, target, "/roles/grant", roleJson("PLAYER")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_ALREADY_ASSIGNED"));
        admin(bearer, target, "/roles/grant", "{\"role\":\"SUPER_USER\",\"reason\":\"x\"}").andExpect(status().isBadRequest());
        admin(bearer, target, "/roles/grant", "{\"role\":\"CONTENT_CREATOR\"}").andExpect(status().isBadRequest());
        admin(bearer, admin, "/roles/grant", roleJson("CONTENT_CREATOR")).andExpect(status().isForbidden());
        admin(bearer, admin, "/roles/revoke", roleJson("ADMIN")).andExpect(status().isForbidden());
        admin(bearer, UUID.randomUUID(), "/roles/grant", roleJson("CONTENT_CREATOR")).andExpect(status().isNotFound());

        UUID suspended = newAccount();
        admin(bearer, suspended, "/status", statusJson("SUSPENDED")).andExpect(status().isNoContent());
        admin(bearer, suspended, "/roles/grant", roleJson("CONTENT_CREATOR")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TARGET_NOT_ACTIVE"));
        assertThat(roles(admin)).contains("ADMIN");
    }

    @Test
    void eszamanliAyniRolVermedenYalnizBiriBasarili() throws Exception {
        String bearer = bearerFor(newAdmin());
        UUID target = newAccount();

        List<Integer> statuses = ConcurrentRequests.run(4, index -> () -> admin(bearer, target, "/roles/grant",
                roleJson("CONTENT_CREATOR")).andReturn().getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(204, 409, 409, 409);
        assertThat(jdbc.queryForObject("select count(*) from account_role_history where account_id = ?", Integer.class,
                target)).isEqualTo(1);
    }

    @Test
    void rolAlinincaYoneticiEskiTokenlaYonetemez() throws Exception {
        UUID admin = newAdmin();
        String adminBearer = bearerFor(admin);
        UUID demoter = newAdmin();
        String demoterBearer = bearerFor(demoter);
        mockMvc.perform(get(AuthApiPaths.ADMIN_ROLES).header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk());

        admin(demoterBearer, admin, "/roles/revoke", roleJson("ADMIN")).andExpect(status().isNoContent());

        mockMvc.perform(get(AuthApiPaths.ADMIN_ROLES).header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isUnauthorized());
    }

    private UUID newAccount() throws Exception {
        return accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
    }

    private UUID newAdmin() throws Exception {
        UUID admin = newAccount();
        enableTotp(admin);
        grantRole(admin, "ADMIN");
        return admin;
    }

    private ResultActions admin(String bearer, UUID accountId, String suffix, String json) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.ADMIN + "/accounts/" + accountId + suffix)
                .header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String statusJson(String status) {
        return "{\"status\":\"%s\",\"reason\":\"%s\"}".formatted(status, REASON);
    }

    private static String roleJson(String role) {
        return "{\"role\":\"%s\",\"reason\":\"%s\"}".formatted(role, REASON);
    }

    private List<String> roles(UUID accountId) {
        return jdbc.queryForList("select r.code from account_roles ar join roles r on r.id = ar.role_id "
                + "where ar.account_id = ?", String.class, accountId);
    }

    private String accountStatus(UUID accountId) {
        return jdbc.queryForObject("select status from accounts where id = ?", String.class, accountId);
    }
}
