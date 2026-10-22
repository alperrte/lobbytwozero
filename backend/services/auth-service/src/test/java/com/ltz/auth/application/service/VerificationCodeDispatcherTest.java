package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.application.port.VerificationCodeMail;
import com.ltz.auth.application.port.VerificationCodeMailPort;
import com.ltz.auth.domain.entity.EmailVerificationCode;
import com.ltz.auth.domain.enums.CodeInvalidationReason;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.domain.enums.VerificationPurpose;
import com.ltz.auth.infrastructure.persistence.repository.EmailVerificationCodeRepository;

class VerificationCodeDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(10);

    private final VerificationCodeMailPort mailPort = mock(VerificationCodeMailPort.class);
    private final TaskExecutor executor = mock(TaskExecutor.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final EmailVerificationCodeRepository codeRepository = mock(EmailVerificationCodeRepository.class);
    private final SecurityEventRecorder recorder = mock(SecurityEventRecorder.class);
    private final VerificationCodeDispatcher dispatcher = new VerificationCodeDispatcher(mailPort, executor,
            transactionTemplate, codeRepository, recorder, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void kuyrukDoluysaKodTeslimEdilemediOlarakGecersizKilinir() {
        UUID accountId = UUID.randomUUID();
        EmailVerificationCode code = EmailVerificationCode.issue(accountId, VerificationPurpose.EMAIL_VERIFICATION,
                "a".repeat(64), NOW, TTL);
        VerificationCodeMail mail = new VerificationCodeMail(UUID.randomUUID(), accountId, "x@example.test", "123456",
                TTL);
        doAnswer(invocation -> {
            throw new TaskRejectedException("queue full");
        }).when(executor).execute(any());
        runTransactionsInline();
        when(codeRepository.findByIdForUpdate(mail.idempotencyKey())).thenReturn(Optional.of(code));

        dispatcher.dispatch(mail);

        assertThat(code.getInvalidationReason()).isEqualTo(CodeInvalidationReason.DELIVERY_FAILED);
        assertThat(code.getInvalidatedAt()).isEqualTo(NOW);
        verify(recorder).record(SecurityEventType.VERIFICATION_CODE_DELIVERY_FAILED, accountId);
        verifyNoInteractions(mailPort);
    }

    @Test
    void zatenTuketilmisKodTeslimHatasindaDegistirilmez() {
        UUID accountId = UUID.randomUUID();
        EmailVerificationCode code = EmailVerificationCode.issue(accountId, VerificationPurpose.EMAIL_VERIFICATION,
                "a".repeat(64), NOW, TTL);
        code.consume(NOW);
        VerificationCodeMail mail = new VerificationCodeMail(UUID.randomUUID(), accountId, "x@example.test", "123456",
                TTL);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).execute(any());
        doAnswer(invocation -> {
            throw new IllegalStateException("smtp down");
        }).when(mailPort).sendVerificationCode(mail);
        runTransactionsInline();
        when(codeRepository.findByIdForUpdate(mail.idempotencyKey())).thenReturn(Optional.of(code));

        dispatcher.dispatch(mail);

        assertThat(code.getInvalidationReason()).isNull();
        verifyNoInteractions(recorder);
    }

    @SuppressWarnings("unchecked")
    private void runTransactionsInline() {
        doAnswer(invocation -> {
            ((Consumer<TransactionStatus>) invocation.getArgument(0)).accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }
}
