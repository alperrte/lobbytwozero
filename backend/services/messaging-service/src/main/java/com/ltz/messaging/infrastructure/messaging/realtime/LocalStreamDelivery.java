package com.ltz.messaging.infrastructure.messaging.realtime;

import java.util.List;
import java.util.UUID;

/** Bu örneğin açık akışlarına iletim; aktarım dinleyicisi ({@link StreamRelayListener}) kullanır. */
public interface LocalStreamDelivery {

    /** Yalnız bu örnekte açık akışı olan alıcılara yazar; diğer zarfları atlar. */
    void deliver(List<StreamEnvelope> envelopes);

    /** Kullanıcının bu örnekteki akışlarını kapatır. */
    void closeStreams(UUID userId);

    /** Bu örnekteki tüm akışları kapatır: istemciler yeniden bağlanıp geçmişten eşitlenir (kaçan olay kalmaz). */
    void closeAllLocal();
}
