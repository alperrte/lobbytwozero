package com.ltz.social.application.service;

import java.time.Instant;

/** Kod sahibinin gördüğü davet kodu. Kod gizli değildir; paylaşılmak üzere üretilir. */
public record InviteCodeView(String code, Instant createdAt, Instant expiresAt, int maxUses, int usedCount) {
}
