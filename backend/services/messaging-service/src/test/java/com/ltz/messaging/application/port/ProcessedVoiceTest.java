package com.ltz.messaging.application.port;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.application.port.VoiceInspectorPort.ProcessedVoice;

/** {@code byte[]} taşıyan kayıt: eşitlik ve karma içeriğe bakar, metin içeriği sızdırmaz. */
class ProcessedVoiceTest {

    @Test
    void esitlikIcerigeBakar() {
        ProcessedVoice voice = new ProcessedVoice("audio/webm", new byte[] {1, 2, 3}, 1500);

        assertThat(voice).isEqualTo(new ProcessedVoice("audio/webm", new byte[] {1, 2, 3}, 1500))
                .hasSameHashCodeAs(new ProcessedVoice("audio/webm", new byte[] {1, 2, 3}, 1500))
                .isNotEqualTo(new ProcessedVoice("audio/webm", new byte[] {1, 2, 4}, 1500))
                .isNotEqualTo(new ProcessedVoice("audio/ogg", new byte[] {1, 2, 3}, 1500))
                .isNotEqualTo(new ProcessedVoice("audio/webm", new byte[] {1, 2, 3}, 1501))
                .isNotEqualTo("audio/webm");
    }

    @Test
    void metinYalnizBoyutuGosterir() {
        assertThat(new ProcessedVoice("audio/webm", new byte[] {9, 9, 9}, 1500))
                .hasToString("ProcessedVoice[mimeType=audio/webm, durationMillis=1500, size=3]");
    }
}
