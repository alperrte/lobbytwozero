package com.ltz.messaging.infrastructure.storage.audio;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * WebM'in kullandığı EBML'nin (RFC 8794) ses için gereken alt kümesi: öğe kimlikleri ve sınırları denetlenen okuma.
 * Her okuma, öğenin ebeveyninin sınırını aşmadığını denetler; dosya dışı okunmaz ve boyut alanı ne derse desin bellek
 * ayrılmaz.
 */
final class Ebml {

    static final int EBML = 0x1A45DFA3;
    static final int DOC_TYPE = 0x4282;
    static final int SEGMENT = 0x18538067;
    static final int SEEK_HEAD = 0x114D9B74;
    static final int INFO = 0x1549A966;
    static final int TRACKS = 0x1654AE6B;
    static final int TRACK_ENTRY = 0xAE;
    static final int TRACK_NUMBER = 0xD7;
    static final int TRACK_TYPE = 0x83;
    static final int CODEC_ID = 0x86;
    static final int CODEC_PRIVATE = 0x63A2;
    static final int CLUSTER = 0x1F43B675;
    static final int TIMESTAMP = 0xE7;
    static final int SIMPLE_BLOCK = 0xA3;
    static final int BLOCK_GROUP = 0xA0;
    static final int BLOCK = 0xA1;
    static final int CUES = 0x1C53BB6B;
    static final int TAGS = 0x1254C367;
    static final int CHAPTERS = 0x1043A770;
    static final int ATTACHMENTS = 0x1941A469;

    private static final int MAX_ID_LENGTH = 4;
    private static final int MAX_SIZE_LENGTH = 8;

    private Ebml() {
    }

    /**
     * Öğe başlığı. {@code end}, boyutu bilinmeyen öğede {@code -1}'dir.
     *
     * @param dataStart öğe verisinin başladığı konum
     */
    record Header(int id, int dataStart, int end) {

        boolean unknownSize() {
            return end < 0;
        }

        int dataLength() {
            return end - dataStart;
        }
    }

    /** {@code position}'daki öğenin başlığını okur; öğe {@code limit}'i aşamaz. */
    static Header readHeader(byte[] file, int position, int limit) throws MalformedAudioException {
        int first = byteAt(file, position, limit);
        int idLength = Integer.numberOfLeadingZeros(first) - 23;
        if (first == 0 || idLength > MAX_ID_LENGTH) {
            throw new MalformedAudioException();
        }
        int id = 0;
        for (int index = 0; index < idLength; index++) {
            id = id << 8 | byteAt(file, position + index, limit);
        }
        int sizeStart = position + idLength;
        int sizeFirst = byteAt(file, sizeStart, limit);
        int sizeLength = Integer.numberOfLeadingZeros(sizeFirst) - 23;
        if (sizeFirst == 0 || sizeLength > MAX_SIZE_LENGTH) {
            throw new MalformedAudioException();
        }
        long size = sizeFirst & (0xFF >>> sizeLength);
        for (int index = 1; index < sizeLength; index++) {
            size = size << 8 | byteAt(file, sizeStart + index, limit);
        }
        int dataStart = sizeStart + sizeLength;
        if (dataStart > limit) {
            throw new MalformedAudioException();
        }
        if (size == (1L << (7 * sizeLength)) - 1) {
            return new Header(id, dataStart, -1);
        }
        if (size > limit - dataStart) {
            throw new MalformedAudioException();
        }
        return new Header(id, dataStart, dataStart + (int) size);
    }

    /** Bilinen boyutlu öğenin işaretsiz tam sayı değeri (en çok 8 bayt). */
    static long readUnsigned(byte[] file, Header header) throws MalformedAudioException {
        requireKnown(header);
        if (header.dataLength() > MAX_SIZE_LENGTH) {
            throw new MalformedAudioException();
        }
        long value = 0;
        for (int index = header.dataStart(); index < header.end(); index++) {
            value = value << 8 | (file[index] & 0xFF);
        }
        return value;
    }

    /** ASCII dizgi; sondaki NUL baytları atılır. */
    static String readString(byte[] file, Header header) throws MalformedAudioException {
        requireKnown(header);
        int end = header.end();
        while (end > header.dataStart() && file[end - 1] == 0) {
            end--;
        }
        return new String(file, header.dataStart(), end - header.dataStart(), StandardCharsets.US_ASCII);
    }

    static void requireKnown(Header header) throws MalformedAudioException {
        if (header.unknownSize()) {
            throw new MalformedAudioException();
        }
    }

    /** Segment'in doğrudan alt öğesi mi (boyutu bilinmeyen Cluster bu kimliklerden birinde biter)? */
    static boolean isTopLevel(int id) {
        return switch (id) {
            case SEEK_HEAD, INFO, TRACKS, CLUSTER, CUES, TAGS, CHAPTERS, ATTACHMENTS -> true;
            default -> false;
        };
    }

    /** Öğeyi en kısa boyut alanıyla yazar. */
    static void writeElement(ByteArrayOutputStream out, int id, byte[] payload) {
        writeHeader(out, id, payload.length);
        out.writeBytes(payload);
    }

    /** Öğe başlığını (kimlik + en kısa boyut alanı) yazar; gövde çağıranda yazılır. */
    static void writeHeader(ByteArrayOutputStream out, int id, long size) {
        for (int shift = 24; shift >= 0; shift -= 8) {
            if ((id >>> shift) != 0 || shift == 0) {
                out.write((id >>> shift) & 0xFF);
            }
        }
        int length = 1;
        // Tüm bitleri 1 olan değer "boyut bilinmiyor" anlamına gelir; o yüzden < 2^(7n) - 1.
        while (size >= (1L << (7 * length)) - 1) {
            length++;
        }
        out.write((int) ((0x100 >>> length) | (size >>> (8 * (length - 1)))));
        for (int index = length - 2; index >= 0; index--) {
            out.write((int) (size >>> (8 * index)));
        }
    }

    /** İşaretsiz tam sayı öğesi, en kısa biçimde (en az 1 bayt). */
    static byte[] unsigned(int id, long value) {
        int length = Math.max(1, (64 - Long.numberOfLeadingZeros(value) + 7) / 8);
        return fixedUnsigned(id, value, length);
    }

    /** İşaretsiz tam sayı öğesi, sabit {@code length} baytlık gövdeyle (konumu önceden hesaplanan Cues için). */
    static byte[] fixedUnsigned(int id, long value, int length) {
        byte[] payload = new byte[length];
        for (int index = 0; index < length; index++) {
            payload[index] = (byte) (value >>> (8 * (length - 1 - index)));
        }
        return element(id, payload);
    }

    static byte[] float64(int id, double value) {
        return fixedUnsigned(id, Double.doubleToLongBits(value), 8);
    }

    static byte[] string(int id, String value) {
        return element(id, value.getBytes(StandardCharsets.US_ASCII));
    }

    static byte[] element(int id, byte[]... parts) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            payload.writeBytes(part);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.size() + 12);
        writeElement(out, id, payload.toByteArray());
        return out.toByteArray();
    }

    private static int byteAt(byte[] file, int position, int limit) throws MalformedAudioException {
        if (position >= limit || position >= file.length) {
            throw new MalformedAudioException();
        }
        return file[position] & 0xFF;
    }
}
