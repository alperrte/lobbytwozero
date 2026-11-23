package com.ltz.messaging.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** İndirilecek içerik: eşitlik içeriğe bakar, metin içeriği sızdırmaz. */
class AttachmentContentTest {

    @Test
    void esitlikIcerigeBakar() {
        AttachmentContent content = new AttachmentContent("image/png", new byte[] {1, 2});

        assertThat(content).isEqualTo(new AttachmentContent("image/png", new byte[] {1, 2}))
                .hasSameHashCodeAs(new AttachmentContent("image/png", new byte[] {1, 2}))
                .isNotEqualTo(new AttachmentContent("image/png", new byte[] {1, 3}))
                .isNotEqualTo(new AttachmentContent("image/gif", new byte[] {1, 2}))
                .isNotEqualTo("image/png");
    }

    @Test
    void metinYalnizBoyutuGosterir() {
        assertThat(new AttachmentContent("image/png", new byte[] {7, 7}))
                .hasToString("AttachmentContent[mimeType=image/png, size=2]");
    }
}
