package com.ltz.lfp.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.lfp.infrastructure.config.ExpiryProperties;
import com.ltz.lfp.infrastructure.persistence.repository.ListingRepository;

import lombok.RequiredArgsConstructor;

/**
 * LFP-15: süresi dolan açık ilanları kalıcı olarak {@code EXPIRED} yapar. Her parti ayrı ve kısa bir transaction'dır;
 * tek çalıştırma en çok {@code maxBatchesPerRun} parti işler, kalan sonraki çalıştırmaya kalır. Okumalar zaten süresi
 * geçmiş ilanı {@code EXPIRED} gösterdiğinden gecikme kullanıcıya yansımaz.
 */
@Service
@RequiredArgsConstructor
public class ListingExpiryService {

    private final ListingRepository listings;
    private final ExpiryProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /** @return bu çalıştırmada işaretlenen ilan sayısı */
    public int expireDue() {
        int total = 0;
        for (int batch = 0; batch < properties.maxBatchesPerRun(); batch++) {
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            int count = transactions.execute(status -> listings.expireDue(now, properties.batchSize()));
            total += count;
            if (count < properties.batchSize()) {
                break;
            }
        }
        return total;
    }
}
