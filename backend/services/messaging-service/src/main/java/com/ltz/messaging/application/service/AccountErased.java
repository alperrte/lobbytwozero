package com.ltz.messaging.application.service;

import java.util.UUID;

/** Hesabın Messaging verisi silindi; commit sonrası açık akışları kapatılır. */
public record AccountErased(UUID accountId) {
}
