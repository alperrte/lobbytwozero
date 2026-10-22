package com.ltz.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;

import com.ltz.auth.application.port.VerificationCodeMailPort;
import com.ltz.auth.infrastructure.client.MailServiceUnavailableAdapter;

class AuthServiceApplicationTests extends IntegrationTestSupport {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void varsayilanBellekIciKullaniciVeUretilmisParolaOlusturulmaz() {
        assertThat(applicationContext.getBeanNamesForType(UserDetailsService.class)).isEmpty();
    }

    @Test
    void uretimMailAdaptoruTeslimiAcikcaReddeder() {
        assertThat(applicationContext.getBean(VerificationCodeMailPort.class))
                .isInstanceOf(MailServiceUnavailableAdapter.class);
    }
}
