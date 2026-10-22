package com.ltz.auth.application.service;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.application.port.VerificationCodeMail;
import com.ltz.auth.application.port.VerificationCodeMailPort;
import com.ltz.auth.domain.entity.EmailVerificationCode;
import com.ltz.auth.domain.enums.CodeInvalidationReason;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.infrastructure.persistence.repository.EmailVerificationCodeRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Kodu işlem commit edildikten sonra, istek iş parçacığı dışında Mail portuna iletir. Böylece yanıt süresi
 * teslim süresinden bağımsızdır (hesap varlığı zamanlama ile sızmaz). Teslim kabul edilmezse kod
 * DELIVERY_FAILED olarak geçersiz kılınır; yeniden gönderme yeni bir kod üretir.
 */
@Slf4j
@Service
public class VerificationCodeDispatcher {

    private final VerificationCodeMailPort mailPort;
    private final TaskExecutor taskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final EmailVerificationCodeRepository codeRepository;
    private final SecurityEventRecorder securityEventRecorder;
    private final Clock clock;

    public VerificationCodeDispatcher(VerificationCodeMailPort mailPort,
                                      @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
                                      TransactionTemplate transactionTemplate,
                                      EmailVerificationCodeRepository codeRepository,
                                      SecurityEventRecorder securityEventRecorder,
                                      Clock clock) {
        this.mailPort = mailPort;
        this.taskExecutor = taskExecutor;
        this.transactionTemplate = transactionTemplate;
        this.codeRepository = codeRepository;
        this.securityEventRecorder = securityEventRecorder;
        this.clock = clock;
    }

    public void dispatch(VerificationCodeMail mail) {
        try {
            taskExecutor.execute(() -> deliver(mail));
        } catch (TaskRejectedException exception) {
            log.warn("Verification code delivery rejected by executor codeId={}", mail.idempotencyKey());
            markDeliveryFailed(mail);
        }
    }

    private void deliver(VerificationCodeMail mail) {
        try {
            mailPort.sendVerificationCode(mail);
        } catch (RuntimeException exception) {
            // Mesaj gövdesi değil yalnız tür loglanır; adaptör hataları alıcı adresi içerebilir.
            log.warn("Verification code delivery failed codeId={} cause={}",
                    mail.idempotencyKey(), exception.getClass().getSimpleName());
            markDeliveryFailed(mail);
        }
    }

    private void markDeliveryFailed(VerificationCodeMail mail) {
        transactionTemplate.executeWithoutResult(status -> codeRepository
                .findByIdForUpdate(mail.idempotencyKey())
                .filter(EmailVerificationCode::isActive)
                .ifPresent(code -> {
                    code.invalidate(CodeInvalidationReason.DELIVERY_FAILED, clock.instant());
                    securityEventRecorder.record(SecurityEventType.VERIFICATION_CODE_DELIVERY_FAILED,
                            mail.accountId());
                }));
    }
}
