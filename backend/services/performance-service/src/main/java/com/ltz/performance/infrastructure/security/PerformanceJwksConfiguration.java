package com.ltz.performance.infrastructure.security;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.Resource;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Configuration(proxyBeanMethods = false)
public class PerformanceJwksConfiguration {
    @Bean(destroyMethod = "close")
    JWKSource<SecurityContext> performanceJwkSource(PerformanceAuthProperties properties) throws IOException {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(properties.readTimeout());
        var client = RestClient.builder().requestFactory(factory).build();
        var uri = properties.jwksUri();
        return JWKSourceBuilder.<SecurityContext>create(uri.toURL(), url -> {
            // Nimbus yalnız yapılandırılmış kaynağa gider; token içindeki jku/x5u asla kullanılmaz.
            if (!uri.toString().equals(url.toString())) throw new IOException("Unexpected JWKS location");
            try {
                return client.get().uri(uri).exchange((request, response) -> {
                    if (response.getStatusCode().value() != 200) throw new IOException("JWKS unavailable");
                    byte[] body = response.getBody().readNBytes(65537);
                    if (body.length > 65536) throw new IOException("JWKS response exceeds limit");
                    return new Resource(new String(body, StandardCharsets.UTF_8), "application/json");
                });
            } catch (RestClientException _) {
                throw new IOException("JWKS unavailable");
            }
        }).cache(300000, 5000).refreshAheadCache(false).retrying(false)
                .rateLimited(1000).outageTolerant(false).build();
    }
}
