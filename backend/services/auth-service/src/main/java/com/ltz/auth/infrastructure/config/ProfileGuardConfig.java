package com.ltz.auth.infrastructure.config;

import java.util.Arrays;
import java.util.Set;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Profil adı yazım hatası (ör. production, staging) prod korumalarını sessizce atlatmasın diye, profil verilmişse
 * dev, test veya prod'dan birini içermek zorundadır. Profil hiç verilmediyse (IDE, yerel çalıştırma) kontrol yapılmaz;
 * güvenliği gevşeten ayarlar bu durumda zaten reddedilir ({@link SecurityProfiles}).
 */
@Configuration(proxyBeanMethods = false)
public class ProfileGuardConfig {

    private static final Set<String> KNOWN = Set.of("dev", "test", "prod");

    ProfileGuardConfig(Environment environment) {
        String[] active = environment.getActiveProfiles();
        if (active.length > 0 && Arrays.stream(active).noneMatch(KNOWN::contains)) {
            throw new IllegalStateException("SPRING_PROFILES_ACTIVE must contain one of: dev, test, prod");
        }
    }
}
