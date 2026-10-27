package com.ltz.hardware.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HardwareAuthProperties.class)
public class HardwareJwtConfiguration {
    @Bean Clock hardwareAuthClock() { return Clock.systemUTC(); }

    @Bean("hardwareUserDecoder")
    JwtDecoder userDecoder(HardwareAuthProperties properties, JWKSource<SecurityContext> source, Clock clock) {
        return decoder(properties, source, clock, false);
    }

    @Bean("hardwareServiceDecoder")
    JwtDecoder serviceDecoder(HardwareAuthProperties properties, JWKSource<SecurityContext> source, Clock clock) {
        return decoder(properties, source, clock, true);
    }

    private JwtDecoder decoder(HardwareAuthProperties properties, JWKSource<SecurityContext> source,
            Clock clock, boolean service) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri().toString())
                .jwsAlgorithm(SignatureAlgorithm.RS256).validateType(false)
                .jwtProcessorCustomizer(processor -> processor.setJWSKeySelector(
                        new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, source))).build();
        decoder.setClaimSetConverter(HardwareJwtValidators::requiredRawClaims);
        decoder.setJwtValidator(HardwareJwtValidators.validator(properties.issuer(),
                service ? "hardware-service" : properties.audience(), service, clock));
        return token -> {
            try { return decoder.decode(token); }
            catch (org.springframework.security.oauth2.jwt.JwtException _) {
                // Includes bounded JWKS failures: fail closed without exposing remote URL or JWT claims.
                throw new org.springframework.security.oauth2.jwt.BadJwtException("Token validation failed");
            }
        };
    }
}
