package com.ltz.messaging.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Ek üst verisi: eşitlik dalga formunun içeriğine bakar, metin içeriği sızdırmaz. */
class AttachmentMetaTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CONVERSATION = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID UPLOADER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant NOW = Instant.parse("2026-11-20T06:00:00Z");

    private static AttachmentMeta meta(UUID id, int size, byte[] waveform) {
        return new AttachmentMeta(id, CONVERSATION, UPLOADER, null, "audio/webm", size, null, null, null, 1500,
                waveform, NOW);
    }

    @Test
    void esitlikDalgaFormuIcerigineBakar() {
        AttachmentMeta meta = meta(ID, 10, new byte[] {1, 2});

        assertThat(meta).isEqualTo(meta(ID, 10, new byte[] {1, 2}))
                .hasSameHashCodeAs(meta(ID, 10, new byte[] {1, 2}))
                .isNotEqualTo(meta(ID, 10, new byte[] {1, 3}))
                .isNotEqualTo(meta(ID, 10, null))
                .isNotEqualTo(meta(ID, 11, new byte[] {1, 2}))
                .isNotEqualTo(meta(UUID.randomUUID(), 10, new byte[] {1, 2}))
                .isNotEqualTo("meta");
        assertThat(meta(ID, 10, null)).isEqualTo(meta(ID, 10, null));
    }

    @Test
    void metinDalgaFormunuAcmazYalnizBoyutuGosterir() {
        assertThat(meta(ID, 10, new byte[] {5, 6, 7}).toString())
                .contains("sizeBytes=10", "waveform=3 bayt").doesNotContain("[B@");
        assertThat(meta(ID, 10, null).toString()).contains("waveform=yok");
    }
}
