package com.ltz.auth.infrastructure.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Süresi dolan geçici kayıtların ve bekleyen hesapların temizliği (SEC-01). Güvenlik olayı saklama süresi
 * (securityEventRetention) uygulama varsayılanında 1 yıldır (docs/database.md); kayıt boş bırakılırsa silme yapılmaz.
 * Süreler birbirinden bağımsız değildir; tutarsız kombinasyonlar açılışta reddedilir ({@link CleanupConfig}).
 *
 * @param pendingAccountTtl      e-postası doğrulanmayan hesabın ömrü; dolunca hesap anonimleştirilir (e-posta ve
 *                               kullanıcı adı yeniden kullanılabilir). Denetim kayıtları silinmez.
 * @param flowGrace              OAuth/OpenID akışı süresi dolduktan sonra silinmeden önce beklenen süre
 * @param nonceRetention         Steam response_nonce'unun tutulma süresi; replay penceresinin en az 2 katı olmalıdır
 * @param throttleRetention      deneme sayaçlarının tutulma süresi; sayma penceresi + kilit süresinden uzun olmalıdır
 * @param tokenRetention         süresi dolan kod/token kayıtlarının tutulma süresi (günlük gönderim sınırı penceresini kapsar)
 * @param sessionRetention       süresi dolan oturumların (ve refresh token'larının) tutulma süresi
 * @param securityEventRetention güvenlik olaylarının saklama süresi (varsayılan 365 gün); <b>boşsa silme yapılmaz</b>
 */
@Validated
@ConfigurationProperties("ltz.auth.cleanup")
public record CleanupProperties(
        boolean enabled,
        @NotNull @DurationMin(minutes = 1) Duration interval,
        @Min(1) @Max(10_000) int batchSize,
        @NotNull @DurationMin(hours = 1) Duration pendingAccountTtl,
        @NotNull @DurationMin(minutes = 1) Duration flowGrace,
        @NotNull @DurationMin(minutes = 1) Duration nonceRetention,
        @NotNull @DurationMin(minutes = 1) Duration throttleRetention,
        @NotNull @DurationMin(days = 1) Duration tokenRetention,
        @NotNull @DurationMin(days = 1) Duration sessionRetention,
        @DurationMin(days = 30) Duration securityEventRetention) {

    public boolean securityEventRetentionConfigured() {
        return securityEventRetention != null;
    }
}
