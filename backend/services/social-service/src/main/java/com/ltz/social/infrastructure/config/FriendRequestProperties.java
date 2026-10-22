package com.ltz.social.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Arkadaşlık isteği kötüye kullanım sınırları. */
@Validated
@ConfigurationProperties("ltz.social.friend-requests")
public record FriendRequestProperties(
        /** Bir kullanıcının aynı anda bekleyen en fazla kaç giden isteği olabilir (toplu istek spam'ine karşı). */
        @Min(1) @Max(1000) int maxPendingOutgoing,
        /** Liste uçlarında sayfa başına varsayılan kayıt. */
        @Min(1) @Max(100) int defaultPageSize,
        /** Liste uçlarında istemcinin isteyebileceği en büyük sayfa boyutu. */
        @Min(1) @Max(100) int maxPageSize) {
}
