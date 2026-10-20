package com.ltz.hardware.domain.entity;

import com.ltz.hardware.domain.enums.CatalogStatus;
import com.ltz.hardware.domain.exception.HardwareConflictException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "hardware_campaigns")
@Getter
public class HardwareCampaign {
    @Id private UUID id;
    @Column(name = "product_id", nullable = false, updatable = false) private UUID productId;
    @Column(nullable = false, length = 160) private String title;
    @Column(name = "source_name", nullable = false, length = 100) private String sourceName;
    @Column(name = "source_url", nullable = false, length = 2048) private String sourceUrl;
    @Column(name = "original_price", nullable = false, precision = 12, scale = 2) private BigDecimal originalPrice;
    @Column(name = "sale_price", nullable = false, precision = 12, scale = 2) private BigDecimal salePrice;
    @JdbcTypeCode(java.sql.Types.CHAR)
    @Column(nullable = false, length = 3, columnDefinition = "char(3)") private String currency;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Column(name = "observed_at", nullable = false) private Instant observedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32) private CatalogStatus status;
    @Version private Long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected HardwareCampaign() { }

    public record Terms(String title, String sourceName, String sourceUrl, BigDecimal originalPrice,
                        BigDecimal salePrice, String currency, Instant startsAt, Instant endsAt, Instant observedAt) { }

    public HardwareCampaign(UUID productId, Terms terms, Instant now) {
        id = UUID.randomUUID();
        this.productId = Objects.requireNonNull(productId);
        status = CatalogStatus.ACTIVE;
        createdAt = now;
        update(terms, now);
    }

    public void update(Terms terms, Instant now) {
        requireActive();
        this.title = CatalogText.clean(terms.title(), 160);
        this.sourceName = CatalogText.clean(terms.sourceName(), 100);
        this.sourceUrl = validUrl(terms.sourceUrl());
        this.originalPrice = price(terms.originalPrice());
        this.salePrice = price(terms.salePrice());
        if (originalPrice.compareTo(salePrice) <= 0) throw new IllegalArgumentException("Geçersiz indirim");
        if (Currency.getInstance(terms.currency()).getDefaultFractionDigits() < 0) {
            throw new IllegalArgumentException("Geçersiz para birimi");
        }
        this.currency = terms.currency();
        validTimes(terms.startsAt(), terms.endsAt(), terms.observedAt(), now);
        this.startsAt = terms.startsAt();
        this.endsAt = terms.endsAt();
        this.observedAt = terms.observedAt();
        updatedAt = now;
    }

    public void requireVersion(long expected) {
        if (version == null || version != expected) throw new HardwareConflictException();
    }

    public void archive(Instant now) {
        requireActive();
        status = CatalogStatus.ARCHIVED;
        updatedAt = now;
    }

    private void requireActive() {
        if (status != CatalogStatus.ACTIVE) throw new HardwareConflictException();
    }

    public BigDecimal discountPercent() {
        return originalPrice.subtract(salePrice).multiply(BigDecimal.valueOf(100))
                .divide(originalPrice, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal price(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.scale() > 2
                || value.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("Geçersiz fiyat");
        }
        return value;
    }

    private static String validUrl(String value) {
        if (value == null || value.length() > 2048) throw new IllegalArgumentException("Geçersiz kaynak");
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Geçersiz kaynak");
        }
        String ascii = uri.toASCIIString();
        if (ascii.length() > 2048) throw new IllegalArgumentException("Geçersiz kaynak");
        return ascii;
    }

    private static void validTimes(Instant starts, Instant ends, Instant observed, Instant now) {
        if (starts == null || ends == null || observed == null || !ends.isAfter(starts) || observed.isAfter(now)) {
            throw new IllegalArgumentException("Geçersiz kampanya zamanı");
        }
    }
}
