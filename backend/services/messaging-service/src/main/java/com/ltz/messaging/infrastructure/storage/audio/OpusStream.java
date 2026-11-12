package com.ltz.messaging.infrastructure.storage.audio;

import java.util.List;

/**
 * Kapsayıcıdan çıkarılmış Opus sesi: başlık değerleri ve doğrulanmış paketler, dosyadaki sırasıyla. Süre kapsayıcının
 * zaman damgalarından değil paketlerden hesaplanır.
 */
public record OpusStream(OpusHead head, List<OpusPacket> packets) {

    public OpusStream {
        packets = List.copyOf(packets);
    }

    public long totalSamples() {
        long total = 0;
        for (OpusPacket packet : packets) {
            total += packet.samples();
        }
        return total;
    }

    /** Çalınacak süre: toplam örnek − pre-skip, milisaniye (aşağı yuvarlanır, en az 0). */
    public long durationMillis() {
        return Math.max(0, totalSamples() - head.preSkip()) * 1000 / OpusHead.SAMPLE_RATE;
    }
}
