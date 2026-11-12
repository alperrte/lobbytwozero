package com.ltz.messaging.infrastructure.storage.audio;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Doğrulanmış tek Opus paketi (RFC 6716 §3). Ses çözülmez; yalnız paket yapısı (TOC baytı, çerçeve düzeni ve
 * uzunlukları, R1–R7 kuralları) denetlenir ve süre TOC'tan hesaplanır. Kod 3 paketinin dolgu (padding) baytları gizli
 * veri taşıyabileceği için atılır: paket aynı çerçevelerle dolgusuz yeniden yazılır. Diğer paketler bayt bayt korunur.
 */
public final class OpusPacket {

    /** Tek çerçevenin azami uzunluğu (R2). */
    static final int MAX_FRAME_BYTES = 1275;
    /** Paket başına azami süre: 120 ms, 48 kHz (R5). */
    static final int MAX_PACKET_SAMPLES = 5760;

    private static final int[] SILK_SAMPLES = {480, 960, 1920, 2880};
    private static final int[] HYBRID_SAMPLES = {480, 960};
    private static final int[] CELT_SAMPLES = {120, 240, 480, 960};
    private static final int PADDING_FLAG = 0x40;
    private static final int VBR_FLAG = 0x80;
    private static final int FRAME_COUNT_MASK = 0x3F;

    private final byte[] bytes;
    private final int samples;

    private OpusPacket(byte[] bytes, int samples) {
        this.bytes = bytes;
        this.samples = samples;
    }

    /** Paketi doğrular; dolgusu varsa atılmış kopyasını döner. */
    public static OpusPacket parse(byte[] raw) throws MalformedAudioException {
        if (raw == null || raw.length == 0) {
            throw new MalformedAudioException();
        }
        int toc = raw[0] & 0xFF;
        int frameSamples = frameSamples(toc >>> 3);
        return switch (toc & 0x03) {
            case 0 -> code0(raw, frameSamples);
            case 1 -> code1(raw, frameSamples);
            case 2 -> code2(raw, frameSamples);
            default -> code3(raw, frameSamples);
        };
    }

    /** Paket baytları (kopya). */
    public byte[] bytes() {
        return bytes.clone();
    }

    /** Paket baytlarının uzunluğu; kopya üretmez. */
    public int length() {
        return bytes.length;
    }

    /** Paket baytlarını hedefe kopyalamadan yazar. */
    public void writeTo(ByteArrayOutputStream out) {
        out.write(bytes, 0, bytes.length);
    }

    /** 48 kHz örnek sayısı. */
    public int samples() {
        return samples;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OpusPacket that && samples == that.samples && Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return 31 * samples + Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "OpusPacket[size=" + bytes.length + ", samples=" + samples + "]";
    }

    static int frameSamples(int config) {
        if (config < 12) {
            return SILK_SAMPLES[config & 0x03];
        }
        if (config < 16) {
            return HYBRID_SAMPLES[config & 0x01];
        }
        return CELT_SAMPLES[config & 0x03];
    }

    private static OpusPacket code0(byte[] raw, int frameSamples) throws MalformedAudioException {
        requireFrameLength(raw.length - 1);
        return new OpusPacket(raw.clone(), frameSamples);
    }

    private static OpusPacket code1(byte[] raw, int frameSamples) throws MalformedAudioException {
        int payload = raw.length - 1;
        if (payload % 2 != 0) {
            throw new MalformedAudioException();
        }
        requireFrameLength(payload / 2);
        return new OpusPacket(raw.clone(), 2 * frameSamples);
    }

    private static OpusPacket code2(byte[] raw, int frameSamples) throws MalformedAudioException {
        Cursor cursor = new Cursor(raw, 1);
        int first = cursor.frameLength();
        int second = raw.length - cursor.position - first;
        if (second < 0) {
            throw new MalformedAudioException();
        }
        requireFrameLength(first);
        requireFrameLength(second);
        return new OpusPacket(raw.clone(), 2 * frameSamples);
    }

    private static OpusPacket code3(byte[] raw, int frameSamples) throws MalformedAudioException {
        if (raw.length < 2) {
            throw new MalformedAudioException();
        }
        int header = raw[1] & 0xFF;
        int frames = header & FRAME_COUNT_MASK;
        if (frames == 0 || frames * frameSamples > MAX_PACKET_SAMPLES) {
            throw new MalformedAudioException();
        }
        Cursor cursor = new Cursor(raw, 2);
        boolean padded = (header & PADDING_FLAG) != 0;
        int padding = padded ? cursor.paddingLength() : 0;
        int lengthsStart = cursor.position;
        int end = raw.length - padding;
        if (end < lengthsStart) {
            throw new MalformedAudioException();
        }
        requireFrames(cursor, (header & VBR_FLAG) != 0, frames, end);
        if (!padded) {
            return new OpusPacket(raw.clone(), frames * frameSamples);
        }
        // Dolgu bayrağı ve uzunluk alanı atılır, çerçeve uzunlukları ve verisi aynen kalır.
        ByteArrayOutputStream out = new ByteArrayOutputStream(end - lengthsStart + 2);
        out.write(raw[0]);
        out.write(header & ~PADDING_FLAG);
        out.write(raw, lengthsStart, end - lengthsStart);
        return new OpusPacket(out.toByteArray(), frames * frameSamples);
    }

    /** Kod 3 çerçeveleri {@code cursor}'dan {@code end}'e kadar: sabit bit hızında eşit, değişkende uzunluklu. */
    private static void requireFrames(Cursor cursor, boolean variable, int frames, int end)
            throws MalformedAudioException {
        if (!variable) {
            int data = end - cursor.position;
            if (data % frames != 0) {
                throw new MalformedAudioException();
            }
            requireFrameLength(data / frames);
            return;
        }
        int total = 0;
        for (int index = 0; index < frames - 1; index++) {
            int length = cursor.frameLength();
            requireFrameLength(length);
            total += length;
        }
        requireFrameLength(end - cursor.position - total);
    }

    private static void requireFrameLength(int length) throws MalformedAudioException {
        if (length < 0 || length > MAX_FRAME_BYTES) {
            throw new MalformedAudioException();
        }
    }

    /** Paket içinde ileri okuyan imleç; dizinin dışına çıkmaz. */
    private static final class Cursor {

        private final byte[] raw;
        private int position;

        Cursor(byte[] raw, int position) {
            this.raw = raw;
            this.position = position;
        }

        /** RFC 6716 §3.2.1: 0–251 tek bayt; 252–255 ise ikinci baytla {@code b0 + 4 * b1}. */
        int frameLength() throws MalformedAudioException {
            int first = next();
            if (first < 252) {
                return first;
            }
            return first + 4 * next();
        }

        /** RFC 6716 §3.2.5: 255 → 254 ekle ve devam et; diğer değer eklenir ve biter. */
        int paddingLength() throws MalformedAudioException {
            int total = 0;
            int value;
            do {
                value = next();
                total += value == 255 ? 254 : value;
            } while (value == 255);
            return total;
        }

        private int next() throws MalformedAudioException {
            if (position >= raw.length) {
                throw new MalformedAudioException();
            }
            return raw[position++] & 0xFF;
        }
    }
}
