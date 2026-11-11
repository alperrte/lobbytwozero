package com.ltz.messaging.infrastructure.storage.audio;

import java.io.Serial;

/**
 * Ses dosyası ya da Opus paketi yapısal olarak geçersiz (MSG-21). Ayrıntı taşımaz ve yığın izi üretmez: kötü niyetli
 * dosyalar ucuza reddedilir, istemciye ve loga yalnız {@code INVALID_AUDIO} yansır.
 */
public final class MalformedAudioException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    public MalformedAudioException() {
        super(null, null, false, false);
    }
}
