package com.ltz.auth.application.service;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ltz.auth.application.port.SecurityNotificationMail;
import com.ltz.auth.application.port.SecurityNotificationMailPort;
import com.ltz.auth.application.port.SecurityNotificationType;

/**
 * Güvenlik ve hesap durumu bildirimlerini, değişikliği yapan işlem <b>commit edildikten sonra</b> ve istek iş parçacığı
 * dışında Mail portuna iletir: geri alınan bir değişiklik için e-posta gitmez, yanıt süresi teslimden bağımsızdır.
 * Teslim en iyi çabayladır; başarısızlık işi bozmaz, yalnız bildirim türü ve hata türü loglanır (alıcı adresi yazılmaz).
 */
@Service
public class SecurityNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SecurityNotificationDispatcher.class);

    private final SecurityNotificationMailPort mailPort;
    private final TaskExecutor taskExecutor;
    private final Clock clock;

    public SecurityNotificationDispatcher(SecurityNotificationMailPort mailPort,
                                          @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor, Clock clock) {
        this.mailPort = mailPort;
        this.taskExecutor = taskExecutor;
        this.clock = clock;
    }

    /** Açık bir işlem varsa commit sonrasında, yoksa hemen gönderir. */
    public void notifyAfterCommit(UUID accountId, String recipientEmail, SecurityNotificationType type) {
        SecurityNotificationMail mail = new SecurityNotificationMail(UUID.randomUUID(), accountId, recipientEmail, type,
                clock.instant());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(mail);
                }
            });
        } else {
            dispatch(mail);
        }
    }

    private void dispatch(SecurityNotificationMail mail) {
        try {
            taskExecutor.execute(() -> deliver(mail));
        } catch (TaskRejectedException _) {
            log.warn("Security notification rejected by executor type={}", mail.type());
        }
    }

    private void deliver(SecurityNotificationMail mail) {
        try {
            mailPort.sendSecurityNotification(mail);
        } catch (RuntimeException exception) {
            // Adaptör hataları alıcı adresini içerebilir; yalnız tür loglanır.
            log.warn("Security notification not delivered type={} cause={}", mail.type(),
                    exception.getClass().getSimpleName());
        }
    }
}
