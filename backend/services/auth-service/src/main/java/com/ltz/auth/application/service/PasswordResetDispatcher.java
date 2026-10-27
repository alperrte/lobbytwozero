package com.ltz.auth.application.service;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.application.port.PasswordResetMail;
import com.ltz.auth.application.port.PasswordResetMailPort;
import com.ltz.auth.domain.entity.PasswordResetToken;
import com.ltz.auth.domain.enums.PasswordResetInvalidationReason;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.infrastructure.persistence.repository.PasswordResetTokenRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Bağlantıyı işlem commit edildikten sonra, istek iş parçacığı dışında Mail portuna iletir; böylece yanıt süresi
 * teslim süresinden (ve hesabın var olup olmadığından) bağımsızdır. Teslim kabul edilmezse token DELIVERY_FAILED
 * olarak geçersiz kılınır; yeni istek yeni token üretir.
 */
@Slf4j
@Service
public class PasswordResetDispatcher {

    private final PasswordResetMailPort mailPort;
    private final TaskExecutor taskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final PasswordResetTokenRepository tokenRepository;
    private final SecurityEventRecorder securityEventRecorder;
    private final Clock clock;

    public PasswordResetDispatcher(PasswordResetMailPort mailPort,
                                   @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
                                   TransactionTemplate transactionTemplate,
                                   PasswordResetTokenRepository tokenRepository,
                                   SecurityEventRecorder securityEventRecorder, Clock clock) {
        this.mailPort = mailPort;
        this.taskExecutor = taskExecutor;
        this.transactionTemplate = transactionTemplate;
        this.tokenRepository = tokenRepository;
        this.securityEventRecorder = securityEventRecorder;
        this.clock = clock;
    }

    public void dispatch(PasswordResetMail mail) {
        try {
            taskExecutor.execute(() -> deliver(mail));
        } catch (TaskRejectedException _) {
            log.warn("Password reset delivery rejected by executor tokenId={}", mail.idempotencyKey());
            markDeliveryFailed(mail);
        }
    }

    private void deliver(PasswordResetMail mail) {
        try {
            mailPort.sendPasswordReset(mail);
        } catch (RuntimeException exception) {
            // Mesaj gövdesi değil yalnız tür loglanır; adaptör hataları alıcı adresini içerebilir.
            log.warn("Password reset delivery failed tokenId={} cause={}", mail.idempotencyKey(),
                    exception.getClass().getSimpleName());
            markDeliveryFailed(mail);
        }
    }

    private void markDeliveryFailed(PasswordResetMail mail) {
        transactionTemplate.executeWithoutResult(status -> tokenRepository
                .findByIdForUpdate(mail.idempotencyKey())
                .filter(PasswordResetToken::isActive)
                .ifPresent(token -> {
                    token.invalidate(PasswordResetInvalidationReason.DELIVERY_FAILED, clock.instant());
                    securityEventRecorder.recordEvent(SecurityEventType.PASSWORD_RESET_DELIVERY_FAILED, mail.accountId());
                }));
    }
}
