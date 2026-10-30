package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.config.ServiceClientProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.security.SecureTokens;
import com.ltz.auth.infrastructure.security.ServiceClientTokenIssuer;

class ServiceClientTokenServiceTest {

    private static final String SECRET = "s".repeat(40);

    private final ServiceClientTokenIssuer issuer = mock(ServiceClientTokenIssuer.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final ServiceClientTokenService service = new ServiceClientTokenService(
            new ServiceClientProperties(Map.of(
                    "admin", new ServiceClientProperties.Client(SECRET, Set.of("account:read")),
                    "social", new ServiceClientProperties.Client(SECRET, Set.of(),
                            Map.of("community-service", Set.of("events:ingest"))),
                    "recommendation", new ServiceClientProperties.Client(SECRET, Set.of(),
                            Map.of("library-service", Set.of("signals:read"))),
                    "off", new ServiceClientProperties.Client("", Set.of("account:read")))),
            issuer, new SecureTokens(), accounts);

    @Test
    void izinliHedefIcinHedefAudienceVeKapsamlaTokenVerilir() {
        when(issuer.issue(eq("social"), eq("community-service"), anySet(), isNull())).thenReturn("community-token");

        ServiceClientTokenService.IssuedServiceToken issued = service.issue("social", SECRET, "community-service");

        assertThat(issued.accessToken()).isEqualTo("community-token");
        assertThat(issued.scope()).isEqualTo("events:ingest");
    }

    @Test
    void izinListesindeOlmayanHedefVeKapsamsizAuthHedefiReddedilirTokenUretilmez() {
        for (String[] attempt : new String[][] {{"social", "notification-service"}, {"social", "auth-service"},
                {"social", null}, {"admin", "community-service"}}) {
            assertThatThrownBy(() -> service.issue(attempt[0], SECRET, attempt[1]))
                    .isInstanceOfSatisfying(AuthException.class, exception -> assertThat(exception.errorCode())
                            .isEqualTo(AuthErrorCode.SERVICE_AUDIENCE_FORBIDDEN));
        }
        verify(issuer, never()).issue(any(), any(), any(), any());
    }

    @Test
    void yanlisAnahtardaHedefIzinliOlsaBileHedefBilgisiSizmazAyniHataDoner() {
        assertThatThrownBy(() -> service.issue("social", "yanlis", "community-service"))
                .isInstanceOfSatisfying(AuthException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(AuthErrorCode.INVALID_CLIENT));
        assertThatThrownBy(() -> service.issue("social", "yanlis", "notification-service"))
                .isInstanceOfSatisfying(AuthException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(AuthErrorCode.INVALID_CLIENT));
    }

    @Test
    void dogruAnahtarlaKayitliKapsamlarlaTokenVerilir() {
        when(issuer.issue(eq("admin"), eq("auth-service"), anySet(), isNull())).thenReturn("signed-token");

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
        verify(issuer, never()).issue(any(), any(), any(), any());
    }

    @Test
    void hesabaBagliKapsamAktifHesapIcinTekHesabaBagliTokenAlir() {
        UUID account = UUID.randomUUID();
        Account active = mock(Account.class);
        when(active.isActive()).thenReturn(true);
        when(accounts.findById(account)).thenReturn(Optional.of(active));
        when(issuer.issue("recommendation", "library-service", Set.of("signals:read"), account)).thenReturn("bound-token");

        ServiceClientTokenService.IssuedServiceToken issued =
                service.issue("recommendation", SECRET, "library-service", account);

        assertThat(issued.accessToken()).isEqualTo("bound-token");
        assertThat(issued.scope()).isEqualTo("signals:read");
    }

    @Test
    void hesapBagiEksikFazlaVeyaHesapAktifDegilseTokenUretilmez() {
        UUID account = UUID.randomUUID();
        Account suspended = mock(Account.class);
        when(accounts.findById(account)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> service.issue("recommendation", SECRET, "library-service", null))
                .isInstanceOfSatisfying(AuthException.class, exception -> assertThat(exception.errorCode())
                        .isEqualTo(AuthErrorCode.SERVICE_ACCOUNT_BINDING_INVALID));
        // Hesaba bağlı olmayan kapsama hesap eklenemez (token'ın anlamı karışmaz).
        assertThatThrownBy(() -> service.issue("social", SECRET, "community-service", account))
                .isInstanceOfSatisfying(AuthException.class, exception -> assertThat(exception.errorCode())
                        .isEqualTo(AuthErrorCode.SERVICE_ACCOUNT_BINDING_INVALID));
        assertThatThrownBy(() -> service.issue("recommendation", SECRET, "library-service", account))
                .isInstanceOfSatisfying(AuthException.class, exception -> assertThat(exception.errorCode())
                        .isEqualTo(AuthErrorCode.SERVICE_TARGET_ACCOUNT_UNAVAILABLE));
        assertThatThrownBy(() -> service.issue("recommendation", SECRET, "library-service", UUID.randomUUID()))
                .isInstanceOfSatisfying(AuthException.class, exception -> assertThat(exception.errorCode())
                        .isEqualTo(AuthErrorCode.SERVICE_TARGET_ACCOUNT_UNAVAILABLE));
        // Kimlik doğrulaması önce: yanlış anahtar hesap bilgisini sızdırmaz.
        assertThatThrownBy(() -> service.issue("recommendation", "yanlis", "library-service", account))
                .isInstanceOfSatisfying(AuthException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(AuthErrorCode.INVALID_CLIENT));
        verify(issuer, never()).issue(any(), any(), any(), any());
    }
}
