package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/** Onarım işçisi: bir tur reconciler'ı çağırır; reconciler çökse bile döngü ayakta kalır (istisna yutulur). */
class AuthEventReconcileWorkerTest {

    private final AuthEventReconciler reconciler = mock(AuthEventReconciler.class);
    private final AuthEventReconcileWorker worker = new AuthEventReconcileWorker(reconciler);

    @Test
    void turYenidenYazilanOlayVarkenDeYokkenDeSorunsuzCalisir() {
        when(reconciler.reconcile()).thenReturn(3, 0);

        assertThatCode(worker::run).doesNotThrowAnyException();
        assertThatCode(worker::run).doesNotThrowAnyException();

        verify(reconciler, times(2)).reconcile();
    }

    @Test
    void reconcilerCokerseIstisnaYutulurDongudenCikmaz() {
        when(reconciler.reconcile()).thenThrow(new IllegalStateException("kisisel@ornek.test"));

        assertThatCode(worker::run).doesNotThrowAnyException();

        verify(reconciler, times(1)).reconcile();
    }
}
