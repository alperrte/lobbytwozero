package com.ltz.mail.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Kuyruk işleyici, retry ve saklama sabitleri (dağıtım sabitleridir, ENV değildir).
 *
 * @param batchSize         bir turda kiralanan en çok kayıt
 * @param lease             kiralama süresi; dolunca çöken işleyicinin kaydı yeniden alınır
 * @param maxAttempts       toplam deneme üst sınırı (aşılınca {@code FAILED})
 * @param baseDelay         ilk yeniden deneme gecikmesi; sonrakiler iki katına çıkar
 * @param maxDelay          gecikme üst sınırı
 * @param notificationMaxAge güvenlik bildirimlerinin gönderilebileceği en uzun süre
 * @param retention         terminal kayıtların saklama süresi
 * @param retentionBatch    bir temizlik turunda silinen en çok kayıt
 */
@Validated
@ConfigurationProperties("ltz.mail.worker")
public record WorkerProperties(boolean enabled, @NotNull Duration pollInterval, @Min(1) @Max(100) int batchSize,
                               @NotNull Duration lease, @Min(1) @Max(20) int maxAttempts,
                               @NotNull Duration baseDelay, @NotNull Duration maxDelay,
                               @NotNull Duration notificationMaxAge, @NotNull Duration retention,
                               @NotNull Duration retentionInterval, @Min(1) @Max(5000) int retentionBatch) {
}
