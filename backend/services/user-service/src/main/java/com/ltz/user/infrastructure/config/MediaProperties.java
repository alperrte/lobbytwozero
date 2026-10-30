package com.ltz.user.infrastructure.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Profil görseli sınırları (K3, docs-user/decisions/kararlar.md). Değerler yük ve kötüye kullanım sınırlarıdır; kod
 * tarafında sabit değer yoktur. Hiçbiri ENV'den gelmez: dağıtım sabitidir.
 *
 * @param root              görsellerin yazıldığı klasör (Docker'da {@code user_media} volume'ü)
 * @param avatarMaxSize     avatar için kabul edilen en büyük yükleme (JPEG/PNG/GIF)
 * @param coverMaxSize      kapak için kabul edilen en büyük yükleme
 * @param avatarLarge       avatarın büyük sürümünün kenarı (kare)
 * @param avatarSmall       avatarın küçük sürümünün kenarı (kare)
 * @param coverLarge        kapağın büyük sürümünün en büyük kenarı
 * @param coverSmall        kapağın küçük sürümünün en büyük kenarı
 * @param maxFrames         GIF için en çok kare
 * @param maxDimension      girdi görselin bir kenarı için üst sınır (piksel)
 * @param maxPixels         girdi görselin toplam piksel üst sınırı (dekompresyon bombası koruması)
 * @param concurrency       aynı anda işlenen görsel sayısı
 * @param busyWait          işlem sırası boş değilse en çok bekleme; sonra {@code 503}
 * @param uploadLimitPerHour kullanıcı başına saatlik yükleme sayısı
 * @param cleanupEnabled    PENDING_DELETE temizleyicisi açık mı
 * @param cleanupInterval   temizleyici aralığı
 */
@Validated
@ConfigurationProperties("ltz.user.media")
public record MediaProperties(
        @NotNull Path root,
        @DefaultValue("2MB") @NotNull DataSize avatarMaxSize,
        @DefaultValue("4MB") @NotNull DataSize coverMaxSize,
        @DefaultValue("512") @Min(16) @Max(4096) int avatarLarge,
        @DefaultValue("128") @Min(16) @Max(4096) int avatarSmall,
        @DefaultValue("1600") @Min(16) @Max(8192) int coverLarge,
        @DefaultValue("800") @Min(16) @Max(8192) int coverSmall,
        @DefaultValue("100") @Min(1) @Max(1000) int maxFrames,
        @DefaultValue("8000") @Min(16) @Max(20000) int maxDimension,
        @DefaultValue("40000000") @Min(1) long maxPixels,
        @DefaultValue("2") @Min(1) @Max(16) int concurrency,
        @DefaultValue("3s") @NotNull Duration busyWait,
        @DefaultValue("10") @Min(1) @Max(1000) int uploadLimitPerHour,
        @DefaultValue("true") boolean cleanupEnabled,
        @DefaultValue("5m") @NotNull Duration cleanupInterval) {
}
