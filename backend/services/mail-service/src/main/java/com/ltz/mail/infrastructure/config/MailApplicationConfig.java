package com.ltz.mail.infrastructure.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import com.ltz.mail.application.port.RecipientResolverPort;
import com.ltz.mail.infrastructure.client.AuthRecipientResolver;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({
        AuthClientProperties.class,
        AuthProperties.class,
        CryptoProperties.class,
        LinkProperties.class,
        PlatformProperties.class,
        RequestLimitProperties.class,
        SmtpProperties.class,
        WorkerProperties.class
})
public class MailApplicationConfig {

    private static final Duration RESOLVER_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration RESOLVER_READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Platform kapısı alıcı çözücüsü (Auth'a servis kimliğiyle bağlanır). Sınırlı bağlantı/okuma zaman aşımı olan ve
     * yönlendirmeleri izlemeyen ayrı bir istemci kurulur; yönlendirme izlenirse token başka adrese gidebilirdi.
     */
    @Bean
    RecipientResolverPort recipientResolver(RestClient.Builder builder, Clock clock, AuthClientProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(RESOLVER_CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
        factory.setReadTimeout(RESOLVER_READ_TIMEOUT);
        RestClient client = builder.clone().baseUrl(properties.baseUrl()).requestFactory(factory).build();
        return new AuthRecipientResolver(client, clock, properties);
    }
}
