package com.ltz.auth.domain.enums;

/** Cihaz kimlik bilgisinin durumu. İptal geri alınmaz; yeni cihaz bağlanır. */
public enum DeviceStatus {
    ACTIVE,
    REVOKED
}
