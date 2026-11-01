package com.ltz.messaging.application.port;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Engelleme verisinin sahibi social-service'tir (MSG-15); Messaging engeli saklamaz, her etkileşimde sorar. Engelin
 * yönü sorulmaz ve bilinmez: iki kullanıcı arasında herhangi bir yönde engel varsa ikisi de etkileşemez.
 */
public interface BlockCheckPort {

    /**
     * @param userId     işlemi yapan kullanıcı
     * @param otherUsers karşı taraflar (en az 1, en fazla 100)
     * @return {@code otherUsers} içinden {@code userId} ile arasında engel olanlar
     * @throws BlockCheckUnavailableException engel durumu öğrenilemezse (çağıran fail-closed davranmalıdır)
     */
    Set<UUID> blockedAmong(UUID userId, Collection<UUID> otherUsers);
}
