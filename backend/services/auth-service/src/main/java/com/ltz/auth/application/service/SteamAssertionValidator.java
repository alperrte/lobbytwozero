package com.ltz.auth.application.service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.ltz.auth.application.port.ExternalAuthenticationException;
import com.ltz.auth.infrastructure.config.OAuthProperties;

/**
 * Steam OpenID 2.0 yanıtının yerel (ağsız) denetimi. Sırayla: ns/mode, op_endpoint, return_to (state dahil birebir),
 * claimed_id/identity biçimi ve tutarlılığı, imzalanan alanların kapsamı, response_nonce zaman penceresi.
 * İmzanın kendisini Steam doğrular (check_authentication); replay denetimi çağıran serviste yapılır.
 */
@Component
public class SteamAssertionValidator {

    static final String OPENID_NS = "http://specs.openid.net/auth/2.0";
    private static final String PREFIX = "openid.";
    private static final Pattern CLAIMED_ID = Pattern.compile("^https://steamcommunity\\.com/openid/id/(\\d{17})$");
    private static final List<String> REQUIRED_SIGNED_FIELDS = List.of(
            "op_endpoint", "claimed_id", "identity", "return_to", "response_nonce", "assoc_handle");
    private static final int NONCE_TIMESTAMP_LENGTH = 20;
    private static final int MAX_NONCE_LENGTH = 255;

    private final OAuthProperties.Steam steam;

    public SteamAssertionValidator(OAuthProperties properties) {
        this.steam = properties.steam();
    }

    /** @return doğrulanmış SteamID64 ve replay denetimi için response_nonce */
    public ValidAssertion validate(Map<String, String[]> requestParams, String expectedReturnTo, Instant now) {
        Map<String, String> openId = openIdParams(requestParams);
        require(OPENID_NS.equals(openId.get("openid.ns")), "ns");
        require("id_res".equals(openId.get("openid.mode")), "mode");
        require(steam.openIdEndpoint().toString().equals(openId.get("openid.op_endpoint")), "op_endpoint");
        require(expectedReturnTo.equals(openId.get("openid.return_to")), "return_to");
        String claimedId = openId.get("openid.claimed_id");
        Matcher matcher = claimedId == null ? null : CLAIMED_ID.matcher(claimedId);
        require(matcher != null && matcher.matches(), "claimed_id");
        require(claimedId.equals(openId.get("openid.identity")), "identity");
        require(signedFields(openId).containsAll(REQUIRED_SIGNED_FIELDS), "signed");
        String nonce = openId.get("openid.response_nonce");
        require(nonce != null && nonce.length() >= NONCE_TIMESTAMP_LENGTH && nonce.length() <= MAX_NONCE_LENGTH
                && isFresh(nonce, now), "response_nonce");
        require(openId.get("openid.sig") != null && !openId.get("openid.sig").isBlank(), "sig");
        return new ValidAssertion(matcher.group(1), nonce, openId);
    }

    /** openid.* alanları tek değerli olmalıdır; çoğaltılmış alan (parametre kirliliği) reddedilir. */
    private static Map<String, String> openIdParams(Map<String, String[]> requestParams) {
        Map<String, String> openId = new HashMap<>();
        requestParams.forEach((name, values) -> {
            if (name.startsWith(PREFIX)) {
                require(values != null && values.length == 1 && values[0] != null, "duplicate " + name);
                openId.put(name, values[0]);
            }
        });
        return openId;
    }

    private static List<String> signedFields(Map<String, String> openId) {
        String signed = openId.get("openid.signed");
        return signed == null ? List.of() : Arrays.asList(signed.split(","));
    }

    private boolean isFresh(String nonce, Instant now) {
        try {
            Instant issued = Instant.parse(nonce.substring(0, NONCE_TIMESTAMP_LENGTH));
            return Duration.between(issued, now).abs().compareTo(steam.assertionMaxAge()) <= 0;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private static void require(boolean condition, String field) {
        if (!condition) {
            throw new ExternalAuthenticationException("Steam assertion rejected: " + field);
        }
    }

    public record ValidAssertion(String steamId, String responseNonce, Map<String, String> openIdParams) {
    }
}
