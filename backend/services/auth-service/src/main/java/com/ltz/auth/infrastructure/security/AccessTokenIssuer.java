package com.ltz.auth.infrastructure.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.AuthSession;
import com.ltz.auth.domain.enums.AuthMethod;
import com.ltz.auth.infrastructure.config.JwtProperties;

/** RS256 access token üretir. Roller yalnız veritabanındaki hesaptan gelir; istemci girdisi claim'e girmez. */
@Component
public class AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final JwtSigningKey signingKey;
    private final JwtProperties properties;

    public AccessTokenIssuer(JwtEncoder jwtEncoder, JwtSigningKey signingKey, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.signingKey = signingKey;
        this.properties = properties;
    }

    public IssuedAccessToken issue(Account account, AuthSession session, Instant now) {
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(account.getId().toString())
                .audience(List.of(properties.audience()))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(TokenClaims.SESSION_ID, session.getId().toString())
                .claim(TokenClaims.TOKEN_VERSION, account.getTokenVersion())
                .claim(TokenClaims.ROLES, account.roleCodes().stream().map(Enum::name).sorted().toList())
                .claim(TokenClaims.AUTH_METHODS, session.authMethods().stream().map(AuthMethod::claimValue).toList())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type(TokenClaims.ACCESS_TOKEN_TYPE)
                .keyId(signingKey.keyId())
                .build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(value, expiresAt);
    }

    public record IssuedAccessToken(String value, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedAccessToken[value=***, expiresAt=" + expiresAt + "]";
        }
    }
}
