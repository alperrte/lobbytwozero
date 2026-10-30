package com.ltz.user.application.service;

import com.ltz.user.domain.enums.GuestAccess;
import com.ltz.user.domain.enums.Visibility;

/** Gizlilik ayarlarının tamamı (PUT) ve istemcinin son gördüğü sürüm. */
public record UpdatePrivacyCommand(long expectedVersion, Visibility friendsList, Visibility library,
                                   Visibility hardware, Visibility performance, GuestAccess guestProfileAccess) {
}
