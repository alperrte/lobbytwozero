package com.ltz.mail.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.ltz.mail.domain.enums.NoticeKind;

class PayloadCodecTest {

    @Test
    void roundTripsTheClassicFields() {
        MailPayload payload = new MailPayload("123456", "https://a.test/x", 600, Instant.parse("2026-11-09T12:00:00Z"));

        assertThat(PayloadCodec.decode(PayloadCodec.encode(payload))).isEqualTo(payload);
    }

    @Test
    void roundTripsANoticeWithAndWithoutTheButton() {
        NoticeContent withButton = new NoticeContent(NoticeKind.WARNING, "Başlık", "Birinci\n\nİkinci", "Aç", "https://a.test/x");
        NoticeContent plain = new NoticeContent(NoticeKind.SUCCESS, "T", "M", null, null);

        assertThat(PayloadCodec.decode(PayloadCodec.encode(new MailPayload(null, null, null, null, withButton))).notice())
                .isEqualTo(withButton);
        assertThat(PayloadCodec.decode(PayloadCodec.encode(new MailPayload(null, null, null, null, plain))).notice())
                .isEqualTo(plain);
    }

    /** Kuyrukta eski sürümle (1) yazılmış kayıtlar bekleyebilir; hâlâ okunabilmelidir. */
    @Test
    void stillReadsPayloadsWrittenInTheLegacyFormat() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(1);
            out.writeUTF("654321");
            out.writeUTF("");
            out.writeInt(900);
            out.writeLong(Long.MIN_VALUE);
        }

        MailPayload payload = PayloadCodec.decode(bytes.toByteArray());

        assertThat(payload.code()).isEqualTo("654321");
        assertThat(payload.link()).isNull();
        assertThat(payload.validitySeconds()).isEqualTo(900);
        assertThat(payload.occurredAt()).isNull();
        assertThat(payload.notice()).isNull();
    }

    @Test
    void rejectsUnknownVersionAndCorruptedBytes() {
        assertThatThrownBy(() -> PayloadCodec.decode(new byte[] {9, 0, 0})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PayloadCodec.decode(new byte[] {2})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringNeverPrintsTheContent() {
        MailPayload payload = new MailPayload(null, null, null, null,
                new NoticeContent(NoticeKind.INFO, "Gizli başlık", "Gizli mesaj", null, null));

        assertThat(payload.toString()).doesNotContain("Gizli");
        assertThat(payload.notice().toString()).doesNotContain("Gizli");
    }
}
