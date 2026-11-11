package com.ltz.messaging.infrastructure.storage.audio;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Opus kimlik başlığından (RFC 7845 §5.1) yalnız oynatma için gereken iki değer. Başlık girdiden kopyalanmaz,
 * {@link #toBytes()} ile sıfırdan kurulur: sürüm 1, giriş örnek hızı 48000, çıkış kazancı 0, kanal eşleme ailesi 0.
 * Böylece başlıktaki serbest alanlar (giriş hızı, kazanç, eşleme tablosu, sondaki baytlar) veri taşıyamaz.
 *
 * @param channels 1 ya da 2
 * @param preSkip  çözücünün başta atacağı örnek sayısı (48 kHz)
 */
public record OpusHead(int channels, int preSkip) {

    static final int SIZE = 19;
    static final int SAMPLE_RATE = 48_000;
    private static final byte[] MAGIC = "OpusHead".getBytes(StandardCharsets.US_ASCII);

    public static OpusHead parse(byte[] packet) throws MalformedAudioException {
        if (packet == null || packet.length < SIZE || !Arrays.equals(packet, 0, MAGIC.length, MAGIC, 0, MAGIC.length)) {
            throw new MalformedAudioException();
        }
        int version = packet[8] & 0xFF;
        int channels = packet[9] & 0xFF;
        int family = packet[18] & 0xFF;
        if ((version & 0xF0) != 0 || channels < 1 || channels > 2 || family != 0) {
            throw new MalformedAudioException();
        }
        int preSkip = (packet[10] & 0xFF) | (packet[11] & 0xFF) << 8;
        return new OpusHead(channels, preSkip);
    }

    public byte[] toBytes() {
        byte[] head = new byte[SIZE];
        System.arraycopy(MAGIC, 0, head, 0, MAGIC.length);
        head[8] = 1;
        head[9] = (byte) channels;
        head[10] = (byte) preSkip;
        head[11] = (byte) (preSkip >>> 8);
        head[12] = (byte) SAMPLE_RATE;
        head[13] = (byte) (SAMPLE_RATE >>> 8);
        head[14] = (byte) (SAMPLE_RATE >>> 16);
        head[15] = (byte) (SAMPLE_RATE >>> 24);
        return head;
    }
}
