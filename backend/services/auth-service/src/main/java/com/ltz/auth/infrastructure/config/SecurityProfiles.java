package com.ltz.auth.infrastructure.config;

import org.springframework.core.env.Environment;

/**
 * Güvenliği gevşeten ENV ayarları (2FA zorunluluğunu kaldırma, Secure olmayan çerez) yalnız dev veya test
 * profilinde kabul edilir. Profil adı belirsiz ya da farklıysa (production, staging, boş) sıkı davranış uygulanır.
 */
public final class SecurityProfiles {

    private static final String RELAXED = "dev | test";
    private static final String PROD = "prod";

    private SecurityProfiles() {
    }

    public static boolean relaxedSecurityAllowed(Environment environment) {
        return environment.matchesProfiles(RELAXED) && !environment.matchesProfiles(PROD);
    }
}
