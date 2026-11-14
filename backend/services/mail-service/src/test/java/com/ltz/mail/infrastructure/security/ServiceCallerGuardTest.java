package com.ltz.mail.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

class ServiceCallerGuardTest {

    @Test
    void callerNameStripsTheServicePrefix() {
        assertThat(ServiceCallerGuard.callerName(token("svc:social-service"))).isEqualTo("social-service");
    }

    @Test
    void tokenWithoutSubjectIsRejectedInsteadOfFailingWithNullPointer() {
        Jwt withoutSubject = token(null);

        assertThatThrownBy(() -> ServiceCallerGuard.callerName(withoutSubject))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subjectWithoutServicePrefixIsRejected() {
        Jwt userToken = token("0b3e1c5a-0000-4000-8000-000000000001");

        assertThatThrownBy(() -> ServiceCallerGuard.callerName(userToken))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static Jwt token(String subject) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token").header("alg", "none").claim("scope", "mail:send");
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }
}
