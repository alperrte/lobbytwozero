package com.ltz.messaging.application.service;

import java.util.Collection;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ltz.messaging.application.port.BlockCheckPort;
import com.ltz.messaging.application.port.BlockCheckUnavailableException;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MSG-15: kullanıcı ile karşı taraflar arasında herhangi bir yönde engel varsa etkileşim reddedilir
 * ({@code 403 MESSAGING_NOT_ALLOWED}; engelin yönü ve hangi kişide olduğu söylenmez). Engel durumu öğrenilemezse
 * işlem fail-closed reddedilir ({@code 503 BLOCK_CHECK_UNAVAILABLE}). DB transaction'ı dışında çağrılmalıdır: ağ
 * çağrısı süresince satır kilidi tutulmaz.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlockGuard {

    private final BlockCheckPort blocks;

    public void requireNoBlock(UUID userId, Collection<UUID> others) {
        boolean anyBlocked;
        try {
            anyBlocked = !blocks.blockedAmong(userId, others).isEmpty();
        } catch (BlockCheckUnavailableException exception) {
            // Kullanıcı kimliği ve token loglanmaz; yalnız neden.
            log.warn("Block check unavailable: {}", exception.getMessage());
            throw new MessagingException(MessagingErrorCode.BLOCK_CHECK_UNAVAILABLE, exception);
        }
        if (anyBlocked) {
            throw new MessagingException(MessagingErrorCode.MESSAGING_NOT_ALLOWED);
        }
    }
}
