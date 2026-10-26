package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.config.ServiceClientProperties;
import com.ltz.auth.infrastructure.security.SecureTokens;
import com.ltz.auth.infrastructure.security.ServiceClientTokenIssuer;

class ServiceClientTokenServiceTest {

    private static final String SECRET = "s".repeat(40);

    private final ServiceClientTokenIssuer issuer = mock(ServiceClientTokenIssuer.class);
    private final ServiceClientTokenService service = new ServiceClientTokenService(
            new ServiceClientProperties(Map.of(
                    "admin", new ServiceClientProperties.Client(SECRET, Set.of("account:read")),
                    "off", new ServiceClientProperties.Client("", Set.of("account:read")))),
            issuer, new SecureTokens());

    @Test
    void dogruAnahtarlaKayitliKapsamlarlaTokenVerilir() {
        when(issuer.issue(eq("admin"), anySet())).thenReturn("signed-token");

        ServiceClientTokenService.IssuedServiceToken issued = service.issue("admin", SECRET);

        assertThat(issued.accessToken()).isEqualTo("signed-token");
        assertThat(issued.scope()).isEqualTo("account:read");
        assertThat(issued.expiresIn()).isEqualTo(ServiceClientTokenIssuer.TTL);
        assertThat(issued.toString()).doesNotContain("signed-token");
    }

    @Test
    void yanlisBilinmeyenVeDevreDisiIstemciAyniHatayiAlirTokenUretilmez() {
        for (String[] attempt : new String[][] {{"admin", "yanlis"}, {"admin", null}, {"admin", ""}, {"nobody", SECRET},
                {"off", ""}, {"off", SECRET}, {null, SECRET}}) {
            assertThatThrownBy(() -> service.issue(attempt[0], attempt[1]))
                    .isInstanceOfSatisfying(AuthException.class,
                            exception -> assertThat(exception.errorCode()).isEqualTo(AuthErrorCode.INVALID_CLIENT));
        }
        verify(issuer, never()).issue(any(), any());
    }
}
