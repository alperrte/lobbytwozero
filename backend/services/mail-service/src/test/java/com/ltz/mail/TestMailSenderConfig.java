package com.ltz.mail;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Gerçek {@code SmtpMailSender} yerine kaydedici gönderici enjekte edilir; ağ bağlantısı kurulmaz. */
@TestConfiguration(proxyBeanMethods = false)
public class TestMailSenderConfig {

    @Bean
    @Primary
    RecordingMailSender recordingMailSender() {
        return new RecordingMailSender();
    }
}
