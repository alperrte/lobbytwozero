package com.ltz.user.application.service;

/** Olayın sonucu: işlendi, daha önce işlenmişti ya da bu servis ilgilenmiyor. */
public enum EventOutcome {
    PROCESSED,
    DUPLICATE,
    IGNORED
}
