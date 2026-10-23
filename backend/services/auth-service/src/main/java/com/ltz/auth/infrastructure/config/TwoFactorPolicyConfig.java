package com.ltz.auth.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.ltz.auth.domain.enums.RoleCode;

/** ADMIN için 2FA zorunluluğu yalnız dev/test profilinde kaldırılabilir; aksi halde servis başlamaz. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TwoFactorProperties.class)
public class TwoFactorPolicyConfig {

    TwoFactorPolicyConfig(TwoFactorProperties properties, Environment environment) {
        boolean adminRequired = properties.requiredRoles() != null && properties.requiredRoles().contains(RoleCode.ADMIN);
        if (!adminRequired && !SecurityProfiles.relaxedSecurityAllowed(environment)) {
            throw new IllegalStateException(
                    "ltz.auth.two-factor.required-roles must contain ADMIN unless the 'dev' or 'test' profile is active");
        }
    }
}
