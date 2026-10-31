package com.ltz.review.infrastructure.config;

import com.ltz.review.infrastructure.persistence.repository.AbuseQuotaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Süresi dolmuş pencere sayaçlarını siler; tablo kullanılmayan satırlarla büyümez. */
@Component
public class AbuseQuotaCleanup {
    private final AbuseQuotaRepository quotas;

    public AbuseQuotaCleanup(AbuseQuotaRepository quotas) { this.quotas = quotas; }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void removeExpired() { quotas.removeExpired(); }
}
