package com.ltz.auth.infrastructure.config;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ltz.auth.infrastructure.security.BoundedPasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class PasswordEncoderConfig {

    static final String ARGON2_ENCODING_ID = "argon2";

    // OWASP Password Storage Cheat Sheet önerisi: Argon2id, m=19 MiB, t=2, p=1.
    static final int SALT_LENGTH_BYTES = 16;
    static final int HASH_LENGTH_BYTES = 32;
    static final int PARALLELISM = 1;
    static final int MEMORY_KIB = 19 * 1024;
    static final int ITERATIONS = 2;

    @Bean
    PasswordEncoder passwordEncoder(PasswordHashingProperties properties) {
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(
                SALT_LENGTH_BYTES, HASH_LENGTH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS);
        // Hash önekine algoritma kimliği yazılır; ileride parametre/algoritma geçişi yeniden hash ile yapılabilir.
        PasswordEncoder delegating = new DelegatingPasswordEncoder(
                ARGON2_ENCODING_ID, Map.of(ARGON2_ENCODING_ID, argon2));
        return new BoundedPasswordEncoder(
                delegating, properties.maxConcurrentHashes(), properties.acquireTimeout());
    }
}
