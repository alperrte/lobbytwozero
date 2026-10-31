package com.ltz.review.infrastructure.config;

import com.ltz.review.domain.enums.AbuseAction;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Kullanıcı başına kota sınırları; tek kaynak application.properties (ltz.review.abuse.*). */
@ConfigurationProperties("ltz.review.abuse")
public record AbuseControlProperties(Rule reviewCreate, Rule reviewLike) {
    public AbuseControlProperties {
        if (reviewCreate == null) reviewCreate = new Rule(5, Duration.ofSeconds(60));
        if (reviewLike == null) reviewLike = new Rule(60, Duration.ofSeconds(60));
    }

    public Rule rule(AbuseAction action) {
        return switch (action) {
            case REVIEW_CREATE -> reviewCreate;
            case REVIEW_LIKE -> reviewLike;
        };
    }

    /** Sabit pencerede en çok {@code limit} deneme; pencere tam saniye ve en çok bir gün olmalıdır. */
    public record Rule(int limit, Duration window) {
        public Rule {
            if (limit < 1 || limit > 100_000) throw new IllegalArgumentException("Kota limiti 1–100000 olmalıdır.");
            if (window == null || window.isNegative() || window.isZero() || window.getNano() != 0 || window.compareTo(Duration.ofDays(1)) > 0)
                throw new IllegalArgumentException("Kota penceresi tam saniye olmalı ve 1 saniye–1 gün aralığında kalmalıdır.");
        }
    }
}
