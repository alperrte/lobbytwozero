package com.ltz.user.application.port;

import java.util.Optional;

import org.springframework.core.io.Resource;

/**
 * Profil görsel dosyalarının deposu. Dosya adları sunucunun ürettiği {@code <anahtar>[-s].<uzantı>} biçimindedir;
 * kullanıcının verdiği ad hiçbir zaman kullanılmaz. Uygulama değiştirilebilir (yerel disk bugün; obje depolama sonra) ve
 * URL sözleşmesi değişmez.
 */
public interface MediaStoragePort {

    /** Dosyayı atomik yazar (yarım dosya okunamaz). Yazılamazsa {@link MediaStorageException}. */
    void write(String fileName, byte[] content);

    /** Dosyayı siler; olmayan dosya için hata vermez (idempotent). Silinemezse {@link MediaStorageException}. */
    void delete(String fileName);

    /** Dosyayı okumak için açar; yoksa boş. */
    Optional<Resource> open(String fileName);
}
