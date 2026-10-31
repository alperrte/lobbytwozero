package com.ltz.mail.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/** Zamanlanmış işleyiciler: her tur ilgili servisi çağırır, bir turun hatası zamanlamayı durdurmaz. */
class MailWorkersTest {

    private final MailDispatchService dispatch = mock(MailDispatchService.class);
    private final MailRetentionService retention = mock(MailRetentionService.class);

    @Test
    void gonderimIsleyicisiHerTurServisiCagirir() {
        MailDispatchWorker worker = new MailDispatchWorker(dispatch);
        when(dispatch.dispatchDue()).thenReturn(3, 0);

        worker.run();
        worker.run();

        verify(dispatch, times(2)).dispatchDue();
    }

    @Test
    void gonderimTuruHataVerirseIstisnaDisariTasmaz() {
        when(dispatch.dispatchDue()).thenThrow(new IllegalStateException("veritabanı yok"));

        assertThatCode(() -> new MailDispatchWorker(dispatch).run()).doesNotThrowAnyException();
    }

    @Test
    void saklamaIsleyicisiTemizlikServisiniCagirir() {
        when(retention.purge()).thenReturn(5, 0);
        MailRetentionWorker worker = new MailRetentionWorker(retention);

        worker.run();
        worker.run();

        verify(retention, times(2)).purge();
    }

    @Test
    void saklamaTuruHataVerirseIstisnaDisariTasmaz() {
        when(retention.purge()).thenThrow(new IllegalStateException("veritabanı yok"));

        assertThatCode(() -> new MailRetentionWorker(retention).run()).doesNotThrowAnyException();
    }
}
