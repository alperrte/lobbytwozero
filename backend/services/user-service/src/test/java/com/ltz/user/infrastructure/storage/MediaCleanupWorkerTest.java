package com.ltz.user.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.ltz.user.application.service.MediaCleanupService;

/** Zamanlanmış temizleyici: bir tur hata verse bile zamanlama bozulmaz (istisna dışarı sızmaz). */
class MediaCleanupWorkerTest {

    private final MediaCleanupService cleanup = mock(MediaCleanupService.class);
    private final MediaCleanupWorker worker = new MediaCleanupWorker(cleanup);

    @Test
    void turTemizleyiciyiCagirir() {
        when(cleanup.purge()).thenReturn(3);

        worker.run();

        verify(cleanup).purge();
    }

    @Test
    void turHataVerirseIstisnaSizmazSonrakiTurCalisir() {
        when(cleanup.purge()).thenThrow(new IllegalStateException("db down")).thenReturn(0);

        assertThatCode(worker::run).doesNotThrowAnyException();
        assertThatCode(worker::run).doesNotThrowAnyException();

        verify(cleanup, times(2)).purge();
    }
}
