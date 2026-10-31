package com.ltz.mail.application.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Zamanlanmış kuyruk işleyici. Bir tur hata verirse zamanlama durmaz. {@code ltz.mail.worker.enabled=false} kapatır. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ltz.mail.worker.enabled", havingValue = "true", matchIfMissing = true)
public class MailDispatchWorker {

    private final MailDispatchService dispatch;

    @Scheduled(fixedDelayString = "${ltz.mail.worker.poll-interval:PT5S}",
            initialDelayString = "${ltz.mail.worker.poll-interval:PT5S}")
    void run() {
        try {
            int processed = dispatch.dispatchDue();
            if (processed > 0) {
                log.debug("Mail dispatch round finished processed={}", processed);
            }
        } catch (RuntimeException exception) {
            log.warn("Mail dispatch round failed; it will be retried cause={}", exception.getClass().getSimpleName());
        }
    }
}
