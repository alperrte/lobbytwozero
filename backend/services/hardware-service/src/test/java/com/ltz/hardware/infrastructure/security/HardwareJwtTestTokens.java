package com.ltz.hardware.infrastructure.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

final class HardwareJwtTestTokens {
    private HardwareJwtTestTokens() { }
    static Map<String,Object> claims(UUID account, boolean service) {
        Instant now = Instant.now();
        var result = new HashMap<String,Object>();
        result.put("iss", "ltz-auth");
        result.put("sub", service ? "svc:auth-service" : account.toString());
        result.put("aud", List.of(service ? "hardware-service" : "ltz-api"));
        result.put("iat", now.getEpochSecond()); result.put("nbf", now.getEpochSecond());
        result.put("exp", now.plusSeconds(service ? 30 : 300).getEpochSecond());
        result.put("jti", UUID.randomUUID().toString());
        if (service) { result.put("scope", "account:erase"); result.put("account_id", account.toString()); }
        else result.put("roles", List.of("PLAYER"));
        return result;
    }
    static String signed(RSAKey key, UUID account, boolean service, Consumer<Map<String,Object>> mutate) throws Exception {
        var claims = claims(account, service); mutate.accept(claims);
        return signed(key, new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID())
                .type(new JOSEObjectType(service ? "svc+jwt" : "at+jwt")).build(), claims);
    }
    static String signed(RSAKey key, JWSHeader header, Map<String,Object> claims) throws Exception {
        var jwt = new SignedJWT(header, claimSet(claims));
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
    static JWTClaimsSet claimSet(Map<String,Object> claims) throws Exception {
        var normalized = new HashMap<>(claims);
        normalized.replaceAll((key,value) -> value instanceof Date date ? date.toInstant().getEpochSecond() : value);
        return JWTClaimsSet.parse(normalized);
    }
}
