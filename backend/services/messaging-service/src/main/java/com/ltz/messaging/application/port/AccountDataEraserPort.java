package com.ltz.messaging.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Hesabın Messaging verisinin silinmesi/anonimleştirilmesi (D4; docs-messaging/TASK-07-TASARIM.md §1). Çağıranın
 * transaction'ına katılır; set tabanlıdır ve idempotenttir. Başka hesapların verisi silinmez.
 */
public interface AccountDataEraserPort {

    /** Silinen hesabın yerine yazılan anonim kimlik ("Silinmiş hesap"). */
    UUID ANONYMOUS_ACCOUNT = new UUID(0L, 0L);

    void erase(UUID accountId, Instant now);
}
