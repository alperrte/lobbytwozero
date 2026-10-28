package com.ltz.auth.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cihaz kimliği ayarlarının tutarlılığını açılışta denetler (fail-fast). Günlük bağlama sınırı etkin cihaz sınırından
 * küçükse kullanıcı izin verilen cihaz sayısına bir günde ulaşamaz; bu sessiz bir yanlış yapılandırmadır.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DeviceProperties.class)
public class DeviceConfig {

    DeviceConfig(DeviceProperties properties) {
        if (properties.dailyBindLimit() < properties.maxActive()) {
            throw new IllegalStateException("ltz.auth.device.daily-bind-limit must not be lower than max-active");
        }
    }
}
