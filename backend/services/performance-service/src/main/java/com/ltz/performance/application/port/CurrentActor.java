package com.ltz.performance.application.port;

import java.util.UUID;

public interface CurrentActor {
    /** Doğrulanmış kullanıcı kimliği; silinmekte olan hesap için reddeder. */
    UUID requireUserId();
}
