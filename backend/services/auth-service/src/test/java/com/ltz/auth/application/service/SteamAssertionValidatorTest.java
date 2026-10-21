package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.ltz.auth.application.port.ExternalAuthenticationException;
import com.ltz.auth.infrastructure.config.OAuthProperties;

class SteamAssertionValidatorTest {

    private static final String ENDPOINT = "https://steamcommunity.com/openid/login";
    private static final String RETURN_TO = "https://ltz.example.test/api/v1/auth/oauth/steam/callback?state=abc";
    private static final String STEAM_ID = "76561198000000000";
    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    private final SteamAssertionValidator validator = new SteamAssertionValidator(new OAuthProperties(
            URI.create("https://ltz.example.test"), URI.create("https://ltz.example.test/cb"), Duration.ofMinutes(10),
            Duration.ofSeconds(5), new OAuthProperties.Google("id", "secret", URI.create("https://a.test"),
            URI.create("https://b.test"), URI.create("https://c.test"), "https://accounts.google.com"),
            new OAuthProperties.Steam(URI.create(ENDPOINT), Duration.ofMinutes(5), Duration.ofMinutes(15))));

    @Test
    void gecerliYanitSteamIdVeNonceVerir() {
        var assertion = validator.validate(params(p -> { }), RETURN_TO, NOW);

        assertThat(assertion.steamId()).isEqualTo(STEAM_ID);
        assertThat(assertion.responseNonce()).startsWith("2026-10-21T12:00:00Z");
        assertThat(assertion.openIdParams()).containsKey("openid.sig");
    }

    @Test
    void sahteveEksikAlanlarReddedilir() {
        assertRejected("yanlis ns", p -> p.put("openid.ns", new String[]{"http://evil"}));
        assertRejected("yanlis mode", p -> p.put("openid.mode", new String[]{"cancel"}));
        assertRejected("yanlis op_endpoint", p -> p.put("openid.op_endpoint", new String[]{"https://evil.test/openid/login"}));
        assertRejected("yanlis return_to", p -> p.put("openid.return_to", new String[]{RETURN_TO + "x"}));
        assertRejected("baska state", p -> p.put("openid.return_to",
                new String[]{"https://ltz.example.test/api/v1/auth/oauth/steam/callback?state=other"}));
        assertRejected("steam disi claimed_id", p -> {
            p.put("openid.claimed_id", new String[]{"https://evil.test/openid/id/" + STEAM_ID});
            p.put("openid.identity", new String[]{"https://evil.test/openid/id/" + STEAM_ID});
        });
        assertRejected("kisa steam id", p -> {
            p.put("openid.claimed_id", new String[]{"https://steamcommunity.com/openid/id/123"});
            p.put("openid.identity", new String[]{"https://steamcommunity.com/openid/id/123"});
        });
        assertRejected("identity farkli", p -> p.put("openid.identity",
                new String[]{"https://steamcommunity.com/openid/id/76561198000000001"}));
        assertRejected("imzalanan alan eksik", p -> p.put("openid.signed", new String[]{"signed,op_endpoint,claimed_id"}));
        assertRejected("sig yok", p -> p.remove("openid.sig"));
        assertRejected("nonce yok", p -> p.remove("openid.response_nonce"));
    }

    @Test
    void eskiVeGelecektekiNonceReddedilir() {
        assertRejected("eski nonce", p -> p.put("openid.response_nonce", new String[]{"2026-10-21T11:00:00Zabc"}));
        assertRejected("gelecek nonce", p -> p.put("openid.response_nonce", new String[]{"2026-10-21T13:00:00Zabc"}));
        assertRejected("bozuk zaman", p -> p.put("openid.response_nonce", new String[]{"not-a-timestamp-at-all"}));
    }

    @Test
    void cogaltilmisOpenIdAlaniReddedilir() {
        assertRejected("parametre kirliligi", p -> p.put("openid.claimed_id", new String[]{
                "https://steamcommunity.com/openid/id/" + STEAM_ID, "https://steamcommunity.com/openid/id/76561198000000001"}));
    }

    private void assertRejected(String name, Consumer<Map<String, String[]>> mutation) {
        assertThatThrownBy(() -> validator.validate(params(mutation), RETURN_TO, NOW))
                .as(name).isInstanceOf(ExternalAuthenticationException.class);
    }

    private static Map<String, String[]> params(Consumer<Map<String, String[]>> mutation) {
        Map<String, String[]> params = new HashMap<>();
        params.put("state", new String[]{"abc"});
        params.put("openid.ns", new String[]{SteamAssertionValidator.OPENID_NS});
        params.put("openid.mode", new String[]{"id_res"});
        params.put("openid.op_endpoint", new String[]{ENDPOINT});
        params.put("openid.claimed_id", new String[]{"https://steamcommunity.com/openid/id/" + STEAM_ID});
        params.put("openid.identity", new String[]{"https://steamcommunity.com/openid/id/" + STEAM_ID});
        params.put("openid.return_to", new String[]{RETURN_TO});
        params.put("openid.response_nonce", new String[]{"2026-10-21T12:00:00Zunique-nonce-1"});
        params.put("openid.assoc_handle", new String[]{"1234567890"});
        params.put("openid.signed", new String[]{
                "signed,op_endpoint,claimed_id,identity,return_to,response_nonce,assoc_handle"});
        params.put("openid.sig", new String[]{"c2lnbmF0dXJl"});
        mutation.accept(params);
        return params;
    }
}
