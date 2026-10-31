package com.ltz.performance.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Her token türü için ayrı decoder: tür, audience ve kapsam karışımı yapısal olarak engellenir. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PerformanceAuthProperties.class)
public class PerformanceJwtConfiguration {
    private static final String SERVICE_AUDIENCE = "performance-service";

    @Bean Clock performanceAuthClock() { return Clock.systemUTC(); }

    @Bean("performanceUserDecoder")
    JwtDecoder userDecoder(PerformanceAuthProperties properties, JWKSource<SecurityContext> source, Clock clock) {
        return decoder(properties, source, PerformanceJwtValidators.validator(
                properties.issuer(), properties.audience(), false, clock));
    }

    @Bean("performanceServiceDecoder")
    JwtDecoder serviceDecoder(PerformanceAuthProperties properties, JWKSource<SecurityContext> source, Clock clock) {
        return decoder(properties, source, PerformanceJwtValidators.validator(
                properties.issuer(), SERVICE_AUDIENCE, true, clock));
    }

    @Bean("performanceDeviceDecoder")
    JwtDecoder deviceDecoder(PerformanceAuthProperties properties, JWKSource<SecurityContext> source, Clock clock) {
        return decoder(properties, source, PerformanceDeviceJwtValidator.validator(properties.issuer(), clock));
    }

    private JwtDecoder decoder(PerformanceAuthProperties properties, JWKSource<SecurityContext> source,
            OAuth2TokenValidator<Jwt> validator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri().toString())
                .jwsAlgorithm(SignatureAlgorithm.RS256).validateType(false)
                .jwtProcessorCustomizer(processor -> processor.setJWSKeySelector(
                        new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, source))).build();
        decoder.setClaimSetConverter(PerformanceJwtValidators::requiredRawClaims);
        decoder.setJwtValidator(validator);
        return token -> {
            try { return decoder.decode(token); }
            catch (JwtException _) {
                // JWKS erişim hataları dahil: kapalı başarısızlık; uzak URL veya claim değerleri sızdırılmaz.
                throw new BadJwtException("Token validation failed");
            }
        };
    }
}
