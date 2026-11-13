package com.ltz.lfp.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ltz.lfp.IntegrationTestSupport;
import com.ltz.lfp.domain.enums.Platform;
import com.ltz.lfp.domain.exception.LfpErrorCode;
import com.ltz.lfp.domain.exception.LfpException;

/** Aynı sahibin eşzamanlı ilan açma istekleri açık ilan sınırını aşamaz (sahip advisory kilidi). */
class ListingConcurrencyTest extends IntegrationTestSupport {

    private static final int THREADS = 10;

    @Autowired
    private ListingService listingService;

    @Test
    void esZamanliIsteklerdeTamOlarakSinirKadarIlanAcilir() throws Exception {
        UUID owner = newUser();
        UUID gameId = games.addGame("Concurrent");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        listingService.create(owner, command(gameId));
                        return true;
                    } catch (LfpException exception) {
                        assertThat(exception.errorCode()).isEqualTo(LfpErrorCode.OPEN_LISTING_LIMIT);
                        return false;
                    }
                }));
            }
            start.countDown();
            int created = 0;
            for (Future<Boolean> result : results) {
                created += result.get(30, TimeUnit.SECONDS) ? 1 : 0;
            }

            assertThat(created).isEqualTo(5);
            assertThat(listingCount(owner)).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    private static CreateListingCommand command(UUID gameId) {
        return new CreateListingCommand(gameId, Platform.PC, null, List.of("Support"), 2, null, null, false,
                "Eşzamanlı ilan", null);
    }
}
