package com.ltz.lfp.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ltz.lfp.application.service.ListingExpiryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Süre dolumunu (LFP-15) periyodik çalıştırır. Yalnız {@code ltz.lfp.expiry.enabled=true} iken yüklenir. Veritabanı
 * hatası bir sonraki çalıştırmayı engellemez; logda yalnız olay kodu ve hata türü vardır (SQL/veri yok).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ltz.lfp.expiry.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ListingExpiryWorker {

    private final ListingExpiryService expiry;

    @Scheduled(fixedDelayString = "${ltz.lfp.expiry.interval}", initialDelayString = "${ltz.lfp.expiry.interval}")
    void run() {
        try {
            int expired = expiry.expireDue();
            if (expired > 0) {
                log.info("LFP_LISTINGS_EXPIRED count={}", expired);
            }
        } catch (DataAccessException exception) {
            log.warn("LFP_EXPIRY_FAILED type={}", exception.getClass().getSimpleName());
        }
    }
}
