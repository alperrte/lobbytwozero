package com.ltz.library.application.port;

import com.ltz.library.domain.exception.SummaryDeliveryException;
import java.util.UUID;

/** Mutlak koleksiyon sayılarını profil sahibi servise (User) {@code LibrarySummaryChanged} olarak iletir; en az bir kez. */
public interface LibrarySummaryPublisher {
    /** Adres veya servis kimliği yoksa {@code false}; hiçbir olay denenmez ve deneme hakkı yanmaz. */
    boolean ready();

    /** Başarısızlık {@link SummaryDeliveryException} ile bildirilir; yük, token ve yanıt gövdesi mesaja konmaz. */
    void publish(UUID userId, int collectionCount, int completedCount, int favoriteCount);
}
