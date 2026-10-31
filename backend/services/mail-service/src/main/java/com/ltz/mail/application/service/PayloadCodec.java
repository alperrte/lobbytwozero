package com.ltz.mail.application.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;

/**
 * {@link MailPayload} ↔ bayt dönüşümü (şifrelemeden önce/sonra). Bilinçli olarak JSON kütüphanesinden bağımsız ve
 * sürümlü basit bir ikili biçimdir: ilk bayt biçim sürümüdür, bilinmeyen sürüm okunmaz.
 */
final class PayloadCodec {

    private static final byte FORMAT_VERSION = 1;
    private static final int ABSENT_NUMBER = -1;
    private static final long ABSENT_TIME = Long.MIN_VALUE;

    private PayloadCodec() {
    }

    static byte[] encode(MailPayload payload) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(FORMAT_VERSION);
            out.writeUTF(payload.code() == null ? "" : payload.code());
            out.writeUTF(payload.link() == null ? "" : payload.link());
            out.writeInt(payload.validitySeconds() == null ? ABSENT_NUMBER : payload.validitySeconds());
            out.writeLong(payload.occurredAt() == null ? ABSENT_TIME : payload.occurredAt().toEpochMilli());
            out.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Payload could not be encoded", exception);
        }
    }

    /** @throws IllegalArgumentException bozuk veya bilinmeyen biçimde */
    static MailPayload decode(byte[] encoded) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (in.readByte() != FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported payload format");
            }
            String code = in.readUTF();
            String link = in.readUTF();
            int validity = in.readInt();
            long occurred = in.readLong();
            return new MailPayload(
                    code.isEmpty() ? null : code,
                    link.isEmpty() ? null : link,
                    validity == ABSENT_NUMBER ? null : validity,
                    occurred == ABSENT_TIME ? null : Instant.ofEpochMilli(occurred));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Payload is corrupted", exception);
        }
    }
}
