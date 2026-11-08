package com.ltz.hardware.domain.entity;

import java.math.BigDecimal;

/** Değerlendirme puanı: 1 ile 5 arasında, yarım yıldız (0,5) adımlarıyla. */
public final class HardwareReviewRating {
    private static final BigDecimal MIN = BigDecimal.ONE;
    private static final BigDecimal MAX = BigDecimal.valueOf(5);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private HardwareReviewRating() { }

    public static boolean isValid(BigDecimal value) {
        return value != null && value.compareTo(MIN) >= 0 && value.compareTo(MAX) <= 0
                && value.multiply(TWO).stripTrailingZeros().scale() <= 0;
    }

    /** Geçerli puanı tek ondalıklı (ör. 4.0, 4.5) biçime getirir; null puan null kalır. */
    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) return null;
        if (!isValid(value)) throw new IllegalArgumentException("Geçersiz puan");
        return value.setScale(1);
    }
}
