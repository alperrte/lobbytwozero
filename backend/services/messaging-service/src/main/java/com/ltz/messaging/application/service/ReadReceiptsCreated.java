package com.ltz.messaging.application.service;

import java.util.Map;
import java.util.UUID;

/** Uygulama içi olay: okundu bilgileri commit edildikten sonra alıcıların akışlarına iletilir; alıcı başına bir yük. */
public record ReadReceiptsCreated(Map<UUID, ReadReceiptView> byRecipient) {
}
