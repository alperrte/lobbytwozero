package com.ltz.performance.application.port;

import java.util.UUID;

public interface AccountErasure {
    /** @return silme tamamlandıysa true; kalan kayıt varsa false (çağıran 503 ile yeniden denetir). */
    boolean erase(UUID accountId);
}
