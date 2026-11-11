package com.ltz.messaging.infrastructure.messaging.realtime;

import java.util.UUID;

/**
 * Örnekler arası aktarılan tek bir canlı akış olayı: alıcı, SSE olay adı, isteğe bağlı olay kimliği ve JSON'a
 * çevrilmiş yük. Yük alıcıya göre önceden kurulmuştur; alan örnek yalnız kendi açık akışlarına yazar.
 */
public record StreamEnvelope(UUID recipient, String event, String id, String data) {
}
