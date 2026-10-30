package com.ltz.user.application.service;

import com.ltz.user.application.port.MediaStoragePort;
import com.ltz.user.domain.entity.UserMedia;

/** Bir görsel satırına ait tüm dosyaları (büyük ve varsa küçük sürüm) siler. Dosya yoksa hata vermez. */
final class MediaFiles {

    private MediaFiles() {
    }

    static void delete(MediaStoragePort storage, UserMedia row) {
        storage.delete(row.largeFileName());
        if (row.smallFileName() != null) {
            storage.delete(row.smallFileName());
        }
    }
}
