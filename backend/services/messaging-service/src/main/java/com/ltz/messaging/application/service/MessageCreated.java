package com.ltz.messaging.application.service;

import java.util.List;
import java.util.UUID;

/** Uygulama içi olay: mesaj commit edildikten sonra akışlara iletilir. {@code recipients} gönderen dahil aktif üyeler. */
public record MessageCreated(MessageView message, List<UUID> recipients) {
}
